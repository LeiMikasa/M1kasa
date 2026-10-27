package com.m1kasa.m1kasaorderservice.mq;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasaorderservice.entity.SeckillOrderOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillOrderOutboxMapper;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


@Component
@ConditionalOnProperty(
        prefix = "m1kasa.order.outbox",
        name = "enabled",
        havingValue = "true"
)
public class SeckillOrderOutboxJob {

    private static final Logger log =
            LoggerFactory.getLogger(SeckillOrderOutboxJob.class);

    private final SeckillOrderOutboxMapper orderOutboxMapper;
    private final SeckillOrderCreatedPublisher publisher;

    public SeckillOrderOutboxJob(SeckillOrderOutboxMapper orderOutboxMapper, SeckillOrderCreatedPublisher publisher) {
        this.orderOutboxMapper = orderOutboxMapper;
        this.publisher = publisher;
    }

    //本轮扫描到的位置；扫描到末尾后，从头重试未发送记录
    private long lastScannedId=0;

    @Scheduled(fixedDelay = 2000,initialDelay = 5000)
    public void dispatchPending(){
        List<SeckillOrderOutbox> records=orderOutboxMapper.selectList(
                new LambdaQueryWrapper<SeckillOrderOutbox>()
                        .eq(SeckillOrderOutbox::getStatus,0)
                        .gt(SeckillOrderOutbox::getId,lastScannedId)
                        .orderByAsc(SeckillOrderOutbox::getId)
                        .last("LIMIT 100")
        );
        if (records.isEmpty()){
            lastScannedId=0;
            return;
        }
        for (SeckillOrderOutbox record : records) {
            lastScannedId = record.getId();
            try {
                SeckillOrderCreatedMessage message = new SeckillOrderCreatedMessage(
                        record.getRequestId(),
                        record.getOrderId(),
                        record.getVoucherId(),
                        record.getUserId()
                );
                //等待发布确认，失败或超时会抛出异常
                publisher.send(message).join();
                //确认成功后，只更新仍然处于待发送状态的记录
                // 确认成功后，只更新仍然处于待发送状态的记录
                orderOutboxMapper.update(
                        null,
                        new LambdaUpdateWrapper<SeckillOrderOutbox>()
                                .eq(
                                        SeckillOrderOutbox::getId,
                                        record.getId()
                                )
                                .eq(SeckillOrderOutbox::getStatus, 0)
                                .set(SeckillOrderOutbox::getStatus, 1)
                                .set(
                                        SeckillOrderOutbox::getUpdateTime,
                                        LocalDateTime.now()
                                )
                );
            }catch (RuntimeException ex){
                   //保留待发送状态，后续扫描时继续重试
                log.warn(
                        "订单事件投递失败，outboxId={},orderId={}",
                        record.getId(),
                        record.getOrderId(),
                        ex
                );
            }
        }
    }

}
