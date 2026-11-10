package com.m1kasa.m1kasashopservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasashopservice.entity.ShopSearchOutbox;
import com.m1kasa.m1kasashopservice.mapper.ShopSearchOutboxMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


@Component
@ConditionalOnProperty(
        prefix = "m1kasa.shop.search.outbox",
        name = "enabled",
        havingValue = "true"
)
public class ShopSearchOutboxJob {
    private static final Logger log=
            LoggerFactory.getLogger(ShopSearchOutboxJob.class);

    private static final int BATCH_SIZE=100;
    private final ShopSearchOutboxMapper outboxMapper;
    private final ShopSearchOutboxDispatcher dispatcher;

    public ShopSearchOutboxJob(ShopSearchOutboxMapper outboxMapper, ShopSearchOutboxDispatcher dispatcher) {
        this.outboxMapper = outboxMapper;
        this.dispatcher = dispatcher;
    }
    //上次扫到了哪里
    private long lastScannedId;
    //本轮扫描的终点
    private long cycleUpperBound;

    @XxlJob("shopSearchOutboxRetry")
    public synchronized void dispatchPending(){
        //1.新一轮开始时，固定扫描终点
        if (cycleUpperBound==0){
            ShopSearchOutbox latest = outboxMapper.selectOne(
                    new LambdaQueryWrapper<ShopSearchOutbox>()
                            .select(ShopSearchOutbox::getId)
                            .eq(ShopSearchOutbox::getStatus, 0)
                            .orderByDesc(ShopSearchOutbox::getId)
                            .last("LIMIT 1")
            );
            if (latest==null){
                XxlJobHelper.log("当前没有待发送的店铺搜索任务");
                return;
            }
            cycleUpperBound=latest.getId();
        }
        //2.本轮范围内，按ID向后扫描一批
        List<ShopSearchOutbox> records = outboxMapper.selectList(
                new LambdaQueryWrapper<ShopSearchOutbox>()
                        .eq(ShopSearchOutbox::getStatus, 0)
                        .gt(ShopSearchOutbox::getId, lastScannedId)
                        .le(ShopSearchOutbox::getId, cycleUpperBound)
                        .orderByAsc(ShopSearchOutbox::getId)
                        .last("LIMIT " + BATCH_SIZE)
        );
        if (records.isEmpty()){
            resetScan();
            XxlJobHelper.log("本轮扫描结束，下次从头扫描");
            return;
        }
        //提交本批投递任务
        List<CompletableFuture<Boolean>> batch=
                new ArrayList<>(records.size());
        for (ShopSearchOutbox record : records) {
            CompletableFuture<Boolean> attempt = dispatcher.dispatchOne(record)
                    .handle((unused, error) -> {

                        if (error != null) {
                            log.warn(
                                    "店铺搜索补发失败，outboxId={}, shopId={}",
                                    record.getId(),
                                    record.getShopId(),
                                    error
                            );

                            return false;
                        }

                        return true;
                    });
            batch.add(attempt);
        }
        //4.等待本破本批投递结束
        CompletableFuture.allOf(
                batch.toArray(new CompletableFuture<?>[0])
        ).join();
        int failures=0;
        for (CompletableFuture<Boolean> attempt : batch) {
            if (!Boolean.TRUE.equals(attempt.join())) {
                failures++;
            }
        }
        // 5. 即使部分失败，也继续向后扫描
        // 失败记录仍为 0，下一轮回头重试
        lastScannedId = records.get(records.size() - 1).getId();

        if (lastScannedId >= cycleUpperBound) {
            resetScan();
        }

        XxlJobHelper.log(
                "店铺搜索补发结束，尝试数={}，失败数={}",
                records.size(),
                failures
        );

        if (failures > 0) {
            XxlJobHelper.handleFail(
                    "本批店铺搜索补发失败数=" + failures
            );
        }
    }
    private void resetScan() {
        lastScannedId = 0;
        cycleUpperBound = 0;
    }
}
