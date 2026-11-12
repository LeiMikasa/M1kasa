package com.m1kasa.m1kasashopservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.m1kasashopservice.entity.Shop;
import com.m1kasa.m1kasashopservice.entity.ShopSearchOutbox;
import com.m1kasa.m1kasashopservice.geo.ShopGeoKeys;
import com.m1kasa.m1kasashopservice.mapper.ShopMapper;
import com.m1kasa.m1kasashopservice.mapper.ShopSearchOutboxMapper;
import com.m1kasa.m1kasashopservice.mq.ShopSearchOutboxDispatcher;
import com.m1kasa.m1kasashopservice.service.ShopService;
import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.message.ShopSearchSyncMessage;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl implements ShopService {
    private static final String CACHE_PREFIX=
            "m1kasa:shop:detail:";
    private static final Duration CACHE_TTL=
            Duration.ofMinutes(60);
    private static final Duration EMPTY_TTL=
            Duration.ofMinutes(2);
    private static final Duration REFRESH_AFTER=Duration.ofMinutes(30);
    private record CachedShopDetail(ShopDTO data,long refreshAt){

    }
    private final RedissonClient redissonClient;
    private final ShopSearchOutboxMapper shopSearchOutboxMapper;
    private final Executor shopDetailRebuildExecutor;

    public ShopServiceImpl(RedissonClient redissonClient, ShopSearchOutboxMapper shopSearchOutboxMapper, Executor shopDetailRebuildExecutor, ShopSearchOutboxDispatcher shopSearchOutboxDispatcher, ShopMapper shopMapper,
                           StringRedisTemplate stringRedisTemplate,
                           JsonMapper jsonMapper) {
        this.redissonClient = redissonClient;
        this.shopSearchOutboxMapper = shopSearchOutboxMapper;
        this.shopDetailRebuildExecutor = shopDetailRebuildExecutor;
        this.shopSearchOutboxDispatcher = shopSearchOutboxDispatcher;
        this.shopMapper = shopMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
    }
    private static final Logger log =
            LoggerFactory.getLogger(ShopServiceImpl.class);
    private final ShopSearchOutboxDispatcher shopSearchOutboxDispatcher;
    private final ShopMapper shopMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    private void dispatchSearch(ShopSearchOutbox outbox) {

        shopSearchOutboxDispatcher
                .dispatchOne(outbox)
                .whenComplete((unused, error) -> {

                    if (error != null) {
                        // 任务仍保留在 Outbox，后续可以补发
                        log.warn(
                                "店铺搜索消息即时投递失败，"
                                        + "outboxId={}, shopId={}, requestId={}",
                                outbox.getId(),
                                outbox.getShopId(),
                                outbox.getRequestId(),
                                error
                        );
                    }
                });
    }
    @Override
    public ShopDTO getById(Long id) {
        String key=CACHE_PREFIX+id;
        CachedShopDetail cached = readShopDetail(key);
        if (cached==null){
            return rebuildShopDetail(id,key);
        }
        if (needsRefresh(cached)){
            refreshShopDetailAsync(id,key);
        }
        return cached.data();
    }
    /*
    * 判断是否需要刷新
    * */
    private boolean needsRefresh(CachedShopDetail cached){
        return cached!=null&&cached.data()!=null
                &&cached.refreshAt()<=System.currentTimeMillis();
    }
    private ShopDTO rebuildShopDetail(Long id,String key){
        Shop shop = shopMapper.selectById(id);
        if (shop==null){
            stringRedisTemplate.opsForValue().set(key, "", EMPTY_TTL);
            return null;
        }
        ShopDTO dto=toDTO(shop);
        CachedShopDetail detail = new CachedShopDetail(
                dto, System.currentTimeMillis() + REFRESH_AFTER.toMillis()
        );
        stringRedisTemplate.opsForValue().set(
                key,jsonMapper.writeValueAsString(detail),CACHE_TTL
        );
        return dto;
    }
    private CachedShopDetail readShopDetail(String key){
        String value=stringRedisTemplate.opsForValue().get(key);
        if (value==null){
            return null;
        }
        if (value.isEmpty()){
            return new CachedShopDetail(null,0L);
        }
        JsonNode json = jsonMapper.readTree(value);
        if (json.has("refreshAt")){
            return jsonMapper.treeToValue(json,CachedShopDetail.class);
        }
        return new CachedShopDetail(
                jsonMapper.treeToValue(json, ShopDTO.class),
                0L
        );
    }

    private ShopDTO toDTO(Shop shop) {
        return toDTO(shop,null);
    }
    private ShopDTO toDTO(Shop shop,Double distance){
        return new ShopDTO(
                shop.getId(), shop.getName(), shop.getTypeId(),
                shop.getImages(), shop.getArea(), shop.getAddress(),
                shop.getX(), shop.getY(), shop.getAvgPrice(),
                shop.getSold(), shop.getComments(), shop.getScore(),
                shop.getOpenHours(), distance,shop.getSearchVersion()
        );
    }
    @Override
    @Transactional
    public boolean update(Shop shop) {
       if (shop==null||shop.getId()==null){
           return false;
       }
        Long id = shop.getId();
       //1.锁住这家店的记录，读取最新数据和版本
        Shop before = shopMapper.selectOne(
                new LambdaQueryWrapper<Shop>()
                        .eq(Shop::getId, id)
                        .last("FOR UPDATE")
        );
        if (before==null){
            return false;
        }
        Long currentVersion=before.getSearchVersion();
        if (currentVersion==null||currentVersion<1){
            throw new IllegalStateException("店铺搜索版本不合法");
        }
        //2.递增版本
        long nextVersion = Math.incrementExact(currentVersion);
        LocalDateTime now=LocalDateTime.now();
        shop.setSearchVersion(nextVersion);
        shop.setUpdateTime(now);
        if (shopMapper.updateById(shop)!=1){
            throw new IllegalStateException("更新店铺失败");
        }
        //3.请求可能只传了名称，因此重新查询完整店铺
        Shop after=shopMapper.selectById(id);
        if (after == null) {
            throw new IllegalStateException("无法读取修改后的店铺");
        }
        // 4. 为本次修改生成固定的消息 ID
        String requestId = UUID.randomUUID().toString();

        ShopSearchSyncMessage message = new ShopSearchSyncMessage(
                requestId,
                id,
                nextVersion,
                toDTO(after)
        );
        // 5. 在当前事务中保存待发送记录
        ShopSearchOutbox outbox = new ShopSearchOutbox();
        outbox.setRequestId(requestId);
        outbox.setShopId(id);
        outbox.setShopVersion(nextVersion);
        outbox.setPayload(jsonMapper.writeValueAsString(message));
        outbox.setStatus(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);

        if (shopSearchOutboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("保存店铺搜索同步任务失败");
        }
        // 6. 数据库提交成功后，再清理缓存、更新 GEO
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        dispatchSearch(outbox);
                        stringRedisTemplate.delete(CACHE_PREFIX + id);

                        String member = id.toString();

                        boolean hasPosition =
                                after.getTypeId() != null
                                        && after.getX() != null
                                        && after.getY() != null;

                        if (hasPosition) {
                            stringRedisTemplate.opsForGeo().add(
                                    ShopGeoKeys.byTypes(after.getTypeId()),
                                    new Point(after.getX(), after.getY()),
                                    member
                            );
                        }

                        // 分类变化或没有有效位置时，移除旧分类中的位置
                        if (before.getTypeId() != null
                                && (!hasPosition
                                || !before.getTypeId().equals(
                                after.getTypeId()
                        ))) {

                            stringRedisTemplate.opsForGeo().remove(
                                    ShopGeoKeys.byTypes(before.getTypeId()),
                                    member
                            );
                        }
                    }
                }
        );

        return true;
    }

    @Override
    public List<ShopDTO> listByType(Long typeId, long current) {
        Page<Shop> page=new Page<>(current,5);
        page.setSearchCount(false); //关闭总条数
        LambdaQueryWrapper<Shop> query=new LambdaQueryWrapper<Shop>()
                .eq(Shop::getTypeId,typeId)
                .orderByAsc(Shop::getId);

        return shopMapper.selectPage(page,query)
                .getRecords()
                .stream()
                .map(this::toDTO)
                .toList();
    }
    private static final int PAGE_SIZE = 5;

    @Override
    public List<ShopDTO> listNearByType(Long typeId, long current, double x, double y) {
        long end=current*PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                ShopGeoKeys.byTypes(typeId),
                GeoReference.fromCoordinate(x, y),
                new Distance(5000, Metrics.METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(end)
        );
        if (results==null){
            return List.of();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> gepPage = results
                .getContent().stream()
                .skip((current - 1) * PAGE_SIZE)
                .toList();

        if (gepPage.isEmpty()){
            return List.of();
        }

        List<Long> ids = gepPage.stream().map(
                item -> Long.valueOf(item.getContent().getName())
        ).toList();
        Map<Long,Shop> shopById=new HashMap<>();
        for (Shop shop : shopMapper.selectList(
                new LambdaQueryWrapper<Shop>()
                        .in(Shop::getId, ids)
                        .eq(Shop::getTypeId, typeId)
        )) {
            shopById.put(shop.getId(),shop);
        }
        //SQL的IN查询不保证距离顺序，所以按Redis结果顺序组装
        List<ShopDTO> shops=new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> item : gepPage) {
            Long id = Long.valueOf(item.getContent().getName());
            Shop shop=shopById.get(id);
            if (shop!=null){
                shops.add(toDTO(shop,item.getDistance().getValue()));
            }
        }
        return shops;
    }

    @Override
    public Long create(Shop shop) {
      LocalDateTime now=LocalDateTime.now();
      shop.setSold(0);
      shop.setComments(0);
      shop.setScore(0);
      shop.setCreateTime(now);
      shop.setUpdateTime(now);
      if (shopMapper.insert(shop)!=1){
          throw new IllegalStateException("新增店铺失败");
      }
      Long id=shop.getId();
      TransactionSynchronizationManager.registerSynchronization(
              new TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                      //清除可能存在的“该ID不存在”空值缓存
                      stringRedisTemplate.delete(
                              CACHE_PREFIX+id
                      );
                      stringRedisTemplate.opsForGeo().add(
                              ShopGeoKeys.byTypes(
                                      shop.getTypeId()
                              ),new Point(shop.getX(),shop.getY()),
                              id.toString()
                      );
                  }
              }
      );
      return id;

    }

    @Override
    public List<ShopDTO> searchByName(String name, long current) {
        Page<Shop> page=new Page<>(current,PAGE_SIZE);
        page.setSearchCount(false);
        LambdaQueryWrapper<Shop> wrapper=new LambdaQueryWrapper<>();
        String keyword=name==null?"":name.trim();
        if (!keyword.isEmpty()){
            wrapper.like(Shop::getName,keyword);
        }
        wrapper.orderByAsc(Shop::getId);
        return shopMapper.selectPage(page,wrapper)
                .getRecords()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<ShopDTO> scanForSearch(long afterId, int size) {
        if (afterId<0){
            throw new IllegalArgumentException(
                    "店铺ID游标不能小于0"
            );
        }
        if (size<1||size>200){
            throw new IllegalArgumentException(
                    "每批读取数量必须在1到100之间"
            );
        }
        Page<Shop> page=new Page<>(1,size);
        page.setSearchCount(false);
        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<Shop>()
                .gt(Shop::getId, afterId)
                .orderByAsc(Shop::getId);
        List<Shop> shops = shopMapper.selectPage(page, query)
                .getRecords();
        return shops.stream().map(this::toDTO).toList();
    }
    private RLock shopDetailLock(Long id){
        return redissonClient.getLock("m1kasa:shop:detail:lock:"+id);
    }
    private void refreshShopDetailAsync(Long id,String cacheKey){
       try {
           shopDetailRebuildExecutor.execute(()->{
               try {
                   refreshShopDetail(id, cacheKey);
               } catch (RuntimeException ex) {
                   log.warn("异步刷新店铺详情失败，shopId={}", id, ex);
               }
           });
       }catch (RejectedExecutionException ex){
           log.debug("店铺详情重建线程池已满，shopId={}",id);
       }
    }
    private void refreshShopDetail(Long id,String key){
        RLock lock = shopDetailLock(id);
        if (!lock.tryLock()){
            return;
        }
        try {
            CachedShopDetail latest = readShopDetail(key);
            if (needsRefresh(latest)){
                rebuildShopDetail(id,key);
            }
        }finally {
            lock.unlock();
        }
    }

}
