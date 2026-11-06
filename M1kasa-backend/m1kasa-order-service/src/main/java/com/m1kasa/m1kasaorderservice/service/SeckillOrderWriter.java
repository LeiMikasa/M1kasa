package com.m1kasa.m1kasaorderservice.service;


import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.entity.SeckillOrderOutbox;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.SeckillOrderOutboxMapper;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.mq.SeckillOrderOutboxDispatcher;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SeckillOrderWriter {
    private static final Logger log =
            LoggerFactory.getLogger(SeckillOrderWriter.class);

    private final SeckillOrderOutboxDispatcher outboxDispatcher;
    private final boolean outboxEnabled;
    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillOrderOutboxMapper orderOutboxMapper;

    public SeckillOrderWriter(
            VoucherOrderMapper voucherOrderMapper,
            SeckillOrderOutboxMapper orderOutboxMapper,
            SeckillOrderOutboxDispatcher outboxDispatcher,
            @Value("${m1kasa.order.outbox.enabled:false}") boolean outboxEnabled
    ) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.orderOutboxMapper = orderOutboxMapper;
        this.outboxDispatcher = outboxDispatcher;
        this.outboxEnabled = outboxEnabled;
    }

    @Transactional
    public Long create(SeckillOrderCreateMessage message){
        LocalDateTime now=LocalDateTime.now();
        //1.保存订单
        VoucherOrder order=new VoucherOrder();
        order.setRequestId(message.requestId());
        order.setOrderNo("MK" + UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(message.userId());
        order.setVoucherId(message.voucherId());
        order.setStatus(OrderStatus.WAITING_STOCK);
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

        if (orderOutboxMapper.insert(outbox) != 1 || outbox.getId() == null) {
            throw new IllegalStateException("保存订单 Outbox 失败");
        }

        if (outboxEnabled) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchAfterCommit(outbox);
                        }
                    }
            );
        }

        return orderId;
    }
    private void dispatchAfterCommit(SeckillOrderOutbox outbox) {
        try {
            outboxDispatcher.dispatchOne(outbox)
                    .whenComplete((unused, error) -> {
                        if (error != null) {
                            log.warn(
                                    "订单事件即时投递失败，outboxId={}, orderId={}",
                                    outbox.getId(),
                                    outbox.getOrderId(),
                                    error
                            );
                        }
                    });
        } catch (RuntimeException ex) {
            log.warn(
                    "订单事件即时投递提交失败，outboxId={}",
                    outbox.getId(),
                    ex
            );
        }
    }
}
