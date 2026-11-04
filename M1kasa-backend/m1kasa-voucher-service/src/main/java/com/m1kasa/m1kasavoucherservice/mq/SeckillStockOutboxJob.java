package com.m1kasa.m1kasavoucherservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockOutbox;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockOutboxMapper;
import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "m1kasa.seckill.stock-outbox",
        name = "enabled",
        havingValue = "true"
)
public class SeckillStockOutboxJob {
    private static final Logger log=
            LoggerFactory.getLogger(SeckillStockOutboxJob.class);
    private final SeckillStockOutboxMapper outboxMapper;
    private final SeckillStockDeductedPublisher publisher;
    // 本轮已经扫描到的位置
    private long lastScannedId = 0;
    public SeckillStockOutboxJob(SeckillStockOutboxMapper outboxMapper, SeckillStockDeductedPublisher publisher) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
    }
    //本轮已经扫描到的位置
    @Scheduled(fixedDelay = 2000,initialDelay = 5000)
    public void dispatchPending(){
        List<SeckillStockOutbox> records=outboxMapper.selectList(
                new LambdaQueryWrapper<SeckillStockOutbox>()
                        .eq(SeckillStockOutbox::getStatus,0)
                        .gt(SeckillStockOutbox::getId,lastScannedId)
                        .orderByAsc(SeckillStockOutbox::getId)
                        .last("LIMIT 100")
        );
        if (records.isEmpty()){
          //扫描到末尾,从头寻找仍未发送成功的记录
            lastScannedId=0;
            return;
        }
        for (SeckillStockOutbox record : records) {
            lastScannedId=record.getId();
            try {
                SeckillStockDeductedMessage message=
                        new SeckillStockDeductedMessage(
                                record.getRequestId(),
                                record.getOrderId(),
                                record.getVoucherId(),
                                record.getUserId()
                        );
                //等待发布确认和路由检查
                //失败或超时会抛异常，跳过下面的状态更新
                publisher.send(message).join();
                // 只把仍处于待发送状态的记录标记为已发送
                outboxMapper.update(
                        null,
                        new LambdaUpdateWrapper<SeckillStockOutbox>()
                                .eq(
                                        SeckillStockOutbox::getId,
                                        record.getId()
                                )
                                .eq(SeckillStockOutbox::getStatus, 0)
                                .set(SeckillStockOutbox::getStatus, 1)
                                .set(
                                        SeckillStockOutbox::getUpdateTime,
                                        LocalDateTime.now()
                                )
                );
            }catch (RuntimeException ex){
                // 保留待发送记录，后续重新扫描、投递
                log.warn(
                        "库存成功事件投递失败，outboxId={}, orderId={}",
                        record.getId(),
                        record.getOrderId(),
                        ex
                );
            }
        }

    }
}
