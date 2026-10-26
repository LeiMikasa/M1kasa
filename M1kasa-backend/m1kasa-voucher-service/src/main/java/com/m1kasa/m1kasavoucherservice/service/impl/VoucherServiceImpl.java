package com.m1kasa.m1kasavoucherservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasavoucherservice.constant.SeckillRedisKeys;
import com.m1kasa.m1kasavoucherservice.entity.SeckillVoucher;
import com.m1kasa.m1kasavoucherservice.entity.Voucher;
import com.m1kasa.m1kasavoucherservice.enums.SeckillReserveStatus;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillVoucherMapper;
import com.m1kasa.m1kasavoucherservice.mapper.VoucherMapper;
import com.m1kasa.m1kasavoucherservice.service.VoucherService;
import com.m1kasa.m1kasavoucherservice.web.SeckillVoucherCreateRequest;
import com.m1kasa.voucher.api.VoucherDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VoucherServiceImpl implements VoucherService {

    private final VoucherMapper voucherMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private static final ZoneId SECKILL_ZONE=ZoneId.of("Asia/Shanghai");
    private static final DefaultRedisScript<Long> INIT_SCRIPT=new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT=new DefaultRedisScript<>();
    static {
        INIT_SCRIPT.setLocation(
                new ClassPathResource("lua/seckill_init_lua.lua")
        );
        INIT_SCRIPT.setResultType(Long.class);

        RESERVE_SCRIPT.setLocation(
                new ClassPathResource("lua/seckill_reserve.lua")
        );
        RESERVE_SCRIPT.setResultType(Long.class);
    }
    public VoucherServiceImpl(VoucherMapper voucherMapper, SeckillVoucherMapper seckillVoucherMapper, StringRedisTemplate stringRedisTemplate) {
        this.voucherMapper = voucherMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<VoucherDTO> listByShopId(Long shopId) {
        //查询当前店铺的上架优惠劵
        List<Voucher> vouchers=voucherMapper.selectList(
                new LambdaQueryWrapper<Voucher>()
                        .eq(Voucher::getShopId,shopId)
                        .eq(Voucher::getStatus,1)
                        .orderByAsc(Voucher::getId)
        );

        if (vouchers.isEmpty()){
            return List.of();
        }
        //收集秒杀劵id,普通劵不需要查询秒杀表
        List<Long> seckillIds = vouchers.stream()
                .filter(voucher ->
                        Integer.valueOf(1).equals(voucher.getType()))
                .map(Voucher::getId)
                .toList();
        Map<Long, SeckillVoucher> seckillMap=new HashMap<>();

        //批量查出秒杀信息，避免每张劵都单独访问一次数据库
        if (!seckillIds.isEmpty()){
            List<SeckillVoucher> seckillVouchers=
                    seckillVoucherMapper.selectList(
                            new LambdaQueryWrapper<SeckillVoucher>()
                                    .in(
                                            SeckillVoucher::getVoucherId,
                                            seckillIds
                                    )
                    );
            for (SeckillVoucher seckillVoucher : seckillVouchers) {
                seckillMap.put(
                        seckillVoucher.getVoucherId(),
                        seckillVoucher
                );
            }
        }
        // 根据优惠券 ID，合并基本信息和秒杀信息
        return vouchers.stream()
                .map(voucher -> toDTO(
                        voucher,
                        seckillMap.get(voucher.getId())
                ))
                .toList();
    }

    @Override
    @Transactional
    public Long createSeckillVoucher(SeckillVoucherCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // 跨字段的业务规则，需要在 Service 中判断
        if (!request.endTime().isAfter(request.beginTime())) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }

        if (!request.endTime().isAfter(now)) {
            throw new IllegalArgumentException("结束时间必须晚于当前时间");
        }

        if (request.payValue() > request.actualValue()) {
            throw new IllegalArgumentException("支付金额不能大于抵扣金额");
        }

        // 第一步：保存优惠券基本信息
        Voucher voucher = new Voucher();
        voucher.setShopId(request.shopId());
        voucher.setTitle(request.title());
        voucher.setSubTitle(request.subTitle());
        voucher.setRules(request.rules());
        voucher.setPayValue(request.payValue());
        voucher.setActualValue(request.actualValue());

        voucher.setType(1);   // 秒杀券
        voucher.setStatus(1); // 上架
        voucher.setCreateTime(now);
        voucher.setUpdateTime(now);

        if (voucherMapper.insert(voucher) != 1) {
            throw new IllegalStateException("保存优惠券失败");
        }

        // 插入后，MyBatis-Plus 将数据库生成的主键回填到 voucher
        Long voucherId = voucher.getId();

        // 第二步：使用同一个 ID 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucherId);
        seckillVoucher.setStock(request.stock());
        seckillVoucher.setBeginTime(request.beginTime());
        seckillVoucher.setEndTime(request.endTime());
        seckillVoucher.setCreateTime(now);
        seckillVoucher.setUpdateTime(now);

        if (seckillVoucherMapper.insert(seckillVoucher) != 1) {
            throw new IllegalStateException("保存秒杀信息失败");
        }

        return voucherId;
    }

    @Override
    public boolean initializeSeckillActivity(Long voucherId) {
      if (voucherId==null || voucherId<=0){
          throw new IllegalArgumentException("优惠劵ID必须大于0");
      }
      Voucher voucher = voucherMapper.selectById(voucherId);
      if (voucher==null){
          throw new IllegalArgumentException("优惠劵不存在");
      }
      if (!Integer.valueOf(1).equals(voucher.getType())
              || !Integer.valueOf(1).equals(voucher.getStatus())){
          throw new IllegalArgumentException("只能预热上架的秒杀劵");
      }
      SeckillVoucher seckillVoucher=seckillVoucherMapper.selectById(voucherId);
      if (seckillVoucher==null){
          throw new IllegalArgumentException("秒杀信息不存在");
      }
      if (seckillVoucher.getStock() == null || seckillVoucher.getStock()<0
      || seckillVoucher.getBeginTime() == null || seckillVoucher.getEndTime() == null){
          throw new IllegalArgumentException("秒杀活动信息不完整");
      }

      //数据库中的活动时间按北京时间解释
      long beginMillis=seckillVoucher.getBeginTime()
              .atZone(SECKILL_ZONE)
              .toInstant()
              .toEpochMilli();
      long endMillis = seckillVoucher.getEndTime()
              .atZone(SECKILL_ZONE)
              .toInstant()
              .toEpochMilli();

      if (endMillis <= beginMillis) {
          throw new IllegalArgumentException("结束时间必须晚于开始时间");
      }

      if (System.currentTimeMillis() >= beginMillis) {
          throw new IllegalArgumentException("必须在活动开始前完成预热");
      }
      Long result = stringRedisTemplate.execute(
              INIT_SCRIPT,
              List.of(SeckillRedisKeys.activity(voucherId)),
              seckillVoucher.getStock().toString(),
              Long.toString(beginMillis),
              Long.toString(endMillis)
      );
      if (result==null){
          throw new IllegalArgumentException("Redis 初始化未返回结果");
      }
      return Long.valueOf(1).equals(result);
    }


    @Override
    public SeckillReserveStatus reserveSeckill(Long voucherId, Long userId) {
          if (voucherId==null || voucherId<=0){
              throw new IllegalArgumentException("优惠劵ID不合法");
          }
          if (userId ==null || userId<=0){
              throw new IllegalArgumentException("用户ID不合法");
          }
          String requestId= UUID.randomUUID().toString();
          Long result=stringRedisTemplate.execute(
                  RESERVE_SCRIPT,
                  List.of(
                          SeckillRedisKeys.activity(voucherId),
                          SeckillRedisKeys.buyers(voucherId),
                          SeckillRedisKeys.outbox(voucherId)
                  ),
                  userId.toString(),
                  requestId
          );
          if (result==null){
              throw new IllegalStateException("抢劵脚本未返回结果");
          }
          return switch (Math.toIntExact(result)){
              case 0->SeckillReserveStatus.RESERVED;
              case 1->SeckillReserveStatus.SOLD_OUT;
              case 2->SeckillReserveStatus.DUPLICATE;
              case 3 -> SeckillReserveStatus.NOT_STARTED;
              case 4 -> SeckillReserveStatus.ENDED;
              case 5 -> SeckillReserveStatus.NOT_INITIALIZED;
              default -> throw new IllegalStateException(
                      "抢券脚本返回了未知结果：" + result
              );
          };
    }

    private VoucherDTO toDTO(
            Voucher voucher,
            SeckillVoucher seckill
    ) {
        return new VoucherDTO(
                voucher.getId(),
                voucher.getShopId(),
                voucher.getTitle(),
                voucher.getSubTitle(),
                voucher.getRules(),
                voucher.getPayValue(),
                voucher.getActualValue(),
                voucher.getType(),
                voucher.getStatus(),
                seckill == null ? null : seckill.getStock(),
                seckill == null ? null : seckill.getBeginTime(),
                seckill == null ? null : seckill.getEndTime()
        );
    }
}
