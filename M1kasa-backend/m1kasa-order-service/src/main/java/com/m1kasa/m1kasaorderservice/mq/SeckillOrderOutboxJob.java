package com.m1kasa.m1kasaorderservice.mq;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasaorderservice.entity.SeckillOrderOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillOrderOutboxMapper;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


@Component
@ConditionalOnProperty(
        prefix = "m1kasa.order.outbox",
        name = "enabled",
        havingValue = "true"
)
public class SeckillOrderOutboxJob {

    private static final Logger log =
            LoggerFactory.getLogger(SeckillOrderOutboxJob.class);
    private static final int BATCH_SIZE = 100;
    private final SeckillOrderOutboxMapper orderOutboxMapper;
    private final SeckillOrderOutboxDispatcher dispatcher;
    private long lastScannedId;   //上次扫到哪里
    private long cycleUpperBound;  //本轮扫到哪里结束
    public SeckillOrderOutboxJob(SeckillOrderOutboxMapper orderOutboxMapper, SeckillOrderCreatedPublisher publisher, SeckillOrderOutboxDispatcher dispatcher) {
        this.orderOutboxMapper = orderOutboxMapper;
        this.dispatcher = dispatcher;
    }

    @XxlJob("seckillOrderOutboxRetry")
    public void dispatchPending(){
          if (cycleUpperBound==0){
             //固定这轮遍历的终点，扫到终点后回头重试旧记录
              SeckillOrderOutbox latest = orderOutboxMapper.selectOne(
                      new LambdaQueryWrapper<SeckillOrderOutbox>()
                              .select(SeckillOrderOutbox::getId)
                              .eq(SeckillOrderOutbox::getStatus, 0)
                              .orderByDesc(SeckillOrderOutbox::getId)
                              .last("LIMIT 1")
              );
              if (latest==null){
                  XxlJobHelper.log("当前没有待发的订单 Outbox");
                  return;
              }
              cycleUpperBound=latest.getId();
          }
        List<SeckillOrderOutbox> records = orderOutboxMapper.selectList(
                new LambdaQueryWrapper<SeckillOrderOutbox>()
                        .eq(SeckillOrderOutbox::getStatus, 0)
                        .gt(SeckillOrderOutbox::getId, lastScannedId)
                        .le(SeckillOrderOutbox::getId, cycleUpperBound)
                        .orderByAsc(SeckillOrderOutbox::getId)
                        .last("LIMIT " + BATCH_SIZE)
        );
        if (records.isEmpty()){
            resetScan();
            XxlJobHelper.log("当前扫描段结束，下次从头扫描");
            return;
        }
        List<CompletableFuture<Boolean>> batch=new ArrayList<>(records.size());
        for (SeckillOrderOutbox record : records) {
            lastScannedId=record.getId();
            CompletableFuture<Boolean> attempt = dispatcher.dispatchOne(record)
                    .handle((unused, error) -> {
                        if (error != null) {
                            log.warn(
                                    "订单事件投递失败，outboxId={}, orderId={}",
                                    record.getId(),
                                    record.getOrderId(),
                                    error
                            );
                            return false;
                        }

                        return true;
                    });
            batch.add(attempt);
        }
        CompletableFuture.allOf(
                batch.toArray(new CompletableFuture<?>[0])
        ).join();

        int failures = 0;
        for (CompletableFuture<Boolean> attempt : batch) {
            if (!Boolean.TRUE.equals(attempt.join())) {
                failures++;
            }
        }

        if (lastScannedId >= cycleUpperBound) {
            resetScan();
        }

        XxlJobHelper.log(
                "订单 Outbox 投递结束，尝试数={}，失败数={}",
                records.size(),
                failures
        );

        if (failures > 0) {
            XxlJobHelper.handleFail("本批投递失败数=" + failures);
        }
    }
    private void resetScan() {
        lastScannedId = 0;
        cycleUpperBound = 0;
    }

}
