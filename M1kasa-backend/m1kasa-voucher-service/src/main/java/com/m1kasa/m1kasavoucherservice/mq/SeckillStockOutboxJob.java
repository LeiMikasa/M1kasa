package com.m1kasa.m1kasavoucherservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasavoucherservice.constant.SeckillStockResultStatus;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockOutbox;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockOutboxMapper;
import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;
import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;
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
        prefix = "m1kasa.seckill.stock-outbox",
        name = "enabled",
        havingValue = "true"
)
public class SeckillStockOutboxJob {
    private static final Logger log=
            LoggerFactory.getLogger(SeckillStockOutboxJob.class);
    private final SeckillStockOutboxMapper outboxMapper;
    private final SeckillStockDeductedPublisher deductedPublisher;
    private final SeckillStockRejectedPublisher rejectedPublisher;
    // 本轮已经扫描到的位置
    private long lastScannedId = 0;

    public SeckillStockOutboxJob(SeckillStockOutboxMapper outboxMapper, SeckillStockDeductedPublisher deductedPublisher, SeckillStockRejectedPublisher rejectedPublisher) {
        this.outboxMapper = outboxMapper;
        this.deductedPublisher = deductedPublisher;
        this.rejectedPublisher = rejectedPublisher;
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
                // 按业务结果发送，并等待发布确认和路由检查
                publishResult(record);
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
                        "库存结果事件投递失败，outboxId={}, orderId={}, resultStatus={}",
                        record.getId(),
                        record.getOrderId(),
                        record.getResultStatus(),
                        ex
                );
            }
        }

    }

    private void publishResult(SeckillStockOutbox record){
        Integer resultStatus=record.getResultStatus();
        if (Integer.valueOf(SeckillStockResultStatus.DEDUCTED)
                .equals(resultStatus)){
            if (StringUtils.hasText(record.getFailureCode())){
                throw new IllegalStateException("库存成功不应该包含拒绝原因");
            }

            SeckillStockDeductedMessage message=
                    new SeckillStockDeductedMessage(
                            record.getRequestId(),
                            record.getOrderId(),
                            record.getVoucherId(),
                            record.getUserId()
                    );
            deductedPublisher.send(message).join();
            return;
        }
        if (Integer.valueOf(SeckillStockResultStatus.REJECTED)
                .equals(resultStatus)){
            if (!StringUtils.hasText(record.getFailureCode())){
                throw new IllegalStateException(
                        "库存拒绝记录缺少拒绝原因"
                );
            }
            SeckillStockRejectedMessage message=
                    new SeckillStockRejectedMessage(
                            record.getRequestId(),
                            record.getOrderId(),
                            record.getVoucherId(),
                            record.getUserId(),
                            record.getFailureCode()
                    );
            rejectedPublisher.send(message).join();
            return;
        }
        throw new IllegalStateException(
                "Outbox 库存处理结果不合法：" + resultStatus
        );
    }

}
