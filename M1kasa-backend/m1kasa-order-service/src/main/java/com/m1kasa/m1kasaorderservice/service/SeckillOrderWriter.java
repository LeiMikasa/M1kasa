package com.m1kasa.m1kasaorderservice.service;


import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.entity.SeckillOrderOutbox;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.SeckillOrderOutboxMapper;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SeckillOrderWriter {
    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillOrderOutboxMapper orderOutboxMapper;

    public SeckillOrderWriter(VoucherOrderMapper voucherOrderMapper, SeckillOrderOutboxMapper orderOutboxMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.orderOutboxMapper = orderOutboxMapper;
    }

    @Transactional
    public Long create(SeckillOrderCreateMessage message){
        LocalDateTime now=LocalDateTime.now();
        //1.保存订单
        VoucherOrder order=new VoucherOrder();
        order.setRequestId(message.requestId());
        order.setRequestId(message.requestId());
        order.setUserId(message.userId());
        order.setVoucherId(message.voucherId());
        order.setStatus(OrderStatus.WAITING_PAYMENT);
        order.setCreateTime(now);
        order.setUpdateTime(now);

        voucherOrderMapper.insert(order);

        // 插入成功后，数据库生成的 ID 已回填到 order
        Long orderId = order.getId();

        // 2. 保存“订单已创建”事件的待发送记录
        SeckillOrderOutbox outbox = new SeckillOrderOutbox();
        outbox.setRequestId(message.requestId());
        outbox.setOrderId(orderId);
        outbox.setVoucherId(message.voucherId());
        outbox.setUserId(message.userId());
        outbox.setStatus(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);

        orderOutboxMapper.insert(outbox);

        return orderId;
    }
}
