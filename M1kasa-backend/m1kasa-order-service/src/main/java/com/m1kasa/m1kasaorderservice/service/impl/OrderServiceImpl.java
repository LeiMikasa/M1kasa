package com.m1kasa.m1kasaorderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderService;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService {
    private final VoucherOrderMapper voucherOrderMapper;

    public OrderServiceImpl(VoucherOrderMapper voucherOrderMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
    }

    @Override
    public Long createSeckillOrder(SeckillOrderCreateMessage message) {
          validateMessage(message);
          VoucherOrder order=new VoucherOrder();
          order.setRequestId(message.requestId());
          order.setUserId(message.userId());
          order.setVoucherId(message.voucherId());
          order.setStatus(OrderStatus.WAITING_PAYMENT);

          LocalDateTime now=LocalDateTime.now();
          order.setCreateTime(now);
          order.setUpdateTime(now);

          try {
              voucherOrderMapper.insert(order);
              //数据库生成的自增ID会回填到实体
              return order.getId();
          }catch (DuplicateKeyException ex){
              //唯一约束冲突，检查是否已经存在对应订单
              return findExistingOrder(message,ex);
          }
    }

    private void validateMessage(
            SeckillOrderCreateMessage message
    ){
        if (message==null
        || !StringUtils.hasText(message.requestId())
        || message.requestId().length()>64){
            throw new IllegalArgumentException(
                    "下单消息的请求ID不合法"
            );
        }
        if (message.userId() == null || message.userId()<=0
        ||message.voucherId()==null || message.voucherId()<=0){
            throw new IllegalArgumentException(
                    "下单消息的用户ID或优惠劵ID不合法"
            );
        }
    }

    private Long findExistingOrder(
            SeckillOrderCreateMessage message,
            DuplicateKeyException cause
    ){
        //优先检查同一个业务请求是否已经创建订单
        VoucherOrder existing = voucherOrderMapper.selectOne(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(
                                VoucherOrder::getRequestId,
                                message.requestId()
                        )
        );
        if (existing!=null){
            //相同请求ID必须对应相同业务内容
            if (!existing.getUserId().equals(message.userId()) ||
            !existing.getVoucherId().equals(message.voucherId())){
                throw new IllegalStateException(
                        "请求ID已存在，但用户或优惠劵不一致"
                        , cause
                );
            }
            return existing.getId();
        }
        //请求ID不同，但可能是同一用户重复购买同一张劵
        existing = voucherOrderMapper.selectOne(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(
                                VoucherOrder::getUserId,
                                message.userId()
                        )
                        .eq(
                                VoucherOrder::getVoucherId,
                                message.voucherId()
                        )
        );
        if (existing!=null){
            return existing.getId();
        }
        //没找到对应订单，不能擅自当做重复消费成功
        throw cause;

    }
}
