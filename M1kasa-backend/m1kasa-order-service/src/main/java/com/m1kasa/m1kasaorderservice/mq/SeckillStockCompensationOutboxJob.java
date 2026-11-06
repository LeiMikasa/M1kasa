package com.m1kasa.m1kasaorderservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasaorderservice.entity.SeckillStockCompensationOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillStockCompensationOutboxMapper;
import com.m1kasa.order.api.message.SeckillOrderClosedMessage;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "m1kasa.order.stock-compensation-outbox",
        name = "enabled",
        havingValue = "true"
)
public class SeckillStockCompensationOutboxJob {

    private static final Logger log =
            LoggerFactory.getLogger(SeckillStockCompensationOutboxJob.class);
    private static final int BATCH_SIZE = 100;

    private final SeckillStockCompensationOutboxMapper outboxMapper;
    private final SeckillOrderClosedPublisher publisher;

    private long lastScannedId;
    private long cycleUpperBound;

    public SeckillStockCompensationOutboxJob(
            SeckillStockCompensationOutboxMapper outboxMapper,
            SeckillOrderClosedPublisher publisher
    ) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
    }

    @XxlJob("seckillStockCompensationRetry")
    public void dispatchPending() {
        try {
            if (cycleUpperBound == 0) {
                List<SeckillStockCompensationOutbox> latest = outboxMapper.selectList(
                        new LambdaQueryWrapper<SeckillStockCompensationOutbox>()
                                .select(SeckillStockCompensationOutbox::getId)
                                .eq(SeckillStockCompensationOutbox::getStatus, 0)
                                .orderByDesc(SeckillStockCompensationOutbox::getId)
                                .last("LIMIT 1")
                );
                if (latest.isEmpty()) {
                    return;
                }
                // 每轮固定扫描上界，持续新增的通知不会让失败记录永远等不到重试。
                cycleUpperBound = latest.get(0).getId();
            }

            List<SeckillStockCompensationOutbox> records = outboxMapper.selectList(
                    new LambdaQueryWrapper<SeckillStockCompensationOutbox>()
                            .eq(SeckillStockCompensationOutbox::getStatus, 0)
                            .gt(SeckillStockCompensationOutbox::getId, lastScannedId)
                            .le(SeckillStockCompensationOutbox::getId, cycleUpperBound)
                            .orderByAsc(SeckillStockCompensationOutbox::getId)
                            .last("LIMIT " + BATCH_SIZE)
            );

            if (records.isEmpty()) {
                resetCycle();
                return;
            }

            for (SeckillStockCompensationOutbox record : records) {
                lastScannedId = record.getId();
                dispatchOne(record);
            }
            if (lastScannedId >= cycleUpperBound) {
                resetCycle();
            }
        } catch (RuntimeException ex) {
            log.warn("扫描库存补偿 Outbox 失败", ex);
        }
    }

    private void dispatchOne(SeckillStockCompensationOutbox record) {
        try {
            SeckillOrderClosedMessage message = toMessage(record);
            publisher.send(message).join();

            // 仅发布检查通过后标记已发送；这不是 Redis 补偿完成的回执。
            outboxMapper.update(
                    null,
                    new LambdaUpdateWrapper<SeckillStockCompensationOutbox>()
                            .eq(SeckillStockCompensationOutbox::getId, record.getId())
                            .eq(SeckillStockCompensationOutbox::getStatus, 0)
                            .set(SeckillStockCompensationOutbox::getStatus, 1)
                            .set(
                                    SeckillStockCompensationOutbox::getUpdateTime,
                                    LocalDateTime.now()
                            )
            );
        } catch (RuntimeException ex) {
            // 超时也可能已投递成功，重发必须沿用业务请求 ID，由消费者幂等处理。
            log.warn(
                    "库存补偿通知投递失败，outboxId={}, orderId={}, requestId={}",
                    record.getId(), record.getOrderId(), record.getRequestId(), ex
            );
        }
    }

    private SeckillOrderClosedMessage toMessage(SeckillStockCompensationOutbox record) {
        if (!StringUtils.hasText(record.getRequestId())
                || record.getRequestId().length() > 64
                || record.getOrderId() == null || record.getOrderId() <= 0
                || record.getVoucherId() == null || record.getVoucherId() <= 0
                || record.getUserId() == null || record.getUserId() <= 0
                || !"STOCK_UNAVAILABLE".equals(record.getFailureCode())) {
            throw new IllegalArgumentException("库存补偿通知业务信息不合法");
        }
        return new SeckillOrderClosedMessage(
                record.getRequestId(), record.getOrderId(),
                record.getVoucherId(), record.getUserId(), record.getFailureCode()
        );
    }

    private void resetCycle() {
        lastScannedId = 0;
        cycleUpperBound = 0;
    }
}
