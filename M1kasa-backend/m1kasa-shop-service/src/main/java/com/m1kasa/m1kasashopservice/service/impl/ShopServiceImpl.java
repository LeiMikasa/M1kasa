package com.m1kasa.m1kasashopservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.m1kasashopservice.entity.Shop;
import com.m1kasa.m1kasashopservice.geo.ShopGeoKeys;
import com.m1kasa.m1kasashopservice.mapper.ShopMapper;
import com.m1kasa.m1kasashopservice.service.ShopService;
import com.m1kasa.shop.api.ShopDTO;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ShopServiceImpl implements ShopService {
    private static final String CACHE_PREFIX=
            "m1kasa:shop:detail:";
    private static final Duration CACHE_TTL=
            Duration.ofMinutes(30);
    private static final Duration EMPTY_TTL=
            Duration.ofMinutes(2);

    public ShopServiceImpl(ShopMapper shopMapper,
                           StringRedisTemplate stringRedisTemplate,
                           JsonMapper jsonMapper) {
        this.shopMapper = shopMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jsonMapper = jsonMapper;
    }

    private final ShopMapper shopMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public ShopDTO getById(Long id) {
        String key=CACHE_PREFIX+id;
        String cached=stringRedisTemplate.opsForValue()
                .get(key);
        if (cached!=null){
            return cached.isEmpty()?null:
                    jsonMapper.readValue(cached, ShopDTO.class);
        }
        Shop shop=shopMapper.selectById(id);
        if (shop==null){
            stringRedisTemplate.opsForValue().set(
                    key,"",EMPTY_TTL
            );
            return null;
        }
        ShopDTO dto=toDTO(shop);
        stringRedisTemplate.opsForValue().set(
                key,jsonMapper.writeValueAsString(dto),CACHE_TTL
        );
        return dto;
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
                shop.getOpenHours(), distance
        );
    }
    @Override
    @Transactional
    public boolean update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            return false;
        }
        //保存更新前的分类，用于从旧分类GEO索引移除
        Shop before= shopMapper.selectById(id);
        if (before==null){
            return false;
        }
        shop.setUpdateTime(LocalDateTime.now());
        if (shopMapper.updateById(shop)==0){
            return false;
        }
        //请求只传部分字段，因此重新查询更新后的完整店铺
        Shop after=shopMapper.selectById(id);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        stringRedisTemplate.delete(CACHE_PREFIX+id);
                        String member=id.toString();
                        boolean hasPosition=after.getTypeId()!=null&&after.getX()!=null&&after.getY()!=null;
                        if (hasPosition){
                            stringRedisTemplate.opsForGeo()
                                    .add(
                                            ShopGeoKeys.byTypes(after.getTypeId()),
                                            new Point(after.getX(),after.getY()),
                                            member
                                    );
                        }
                        //换了分类，或者更新后没有坐标；清理旧分类中的位置
                        if (before.getTypeId() != null
                                && (!hasPosition
                                || !before.getTypeId().equals(after.getTypeId()))) {
                            stringRedisTemplate.opsForGeo().remove(
                                    ShopGeoKeys.byTypes(before.getTypeId()), member
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
}
