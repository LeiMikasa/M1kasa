package com.m1kasa.m1kasavoucherservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasavoucherservice.constant.SeckillRedisKeys;
import com.m1kasa.m1kasavoucherservice.entity.SeckillVoucher;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillVoucherMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/*后台定期找到 Redis 中还没发出去的下单消息，再尝试发送到 RabbitMQ。*/
@Component
@ConditionalOnProperty(
        prefix = "m1kasa.seckill.outbox",
        name = "enabled",
        havingValue = "true"
)
public class SeckillOutboxJob {

    private static final Logger log =
            LoggerFactory.getLogger(SeckillOutboxJob.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOutboxDispatcher dispatcher;

    public SeckillOutboxJob(
            SeckillVoucherMapper seckillVoucherMapper,
            StringRedisTemplate stringRedisTemplate,
            SeckillOutboxDispatcher dispatcher
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dispatcher = dispatcher;
    }

    //本轮任务入口，逐个处理活动，汇总结果
    @XxlJob("seckillOutRetry")
    public void dispatchPending() {
        XxlJobHelper.log("开始扫描秒杀下单 Outbox");
        ScanOptions options = ScanOptions.scanOptions()
                .match(SeckillRedisKeys.OUTBOX_PREFIX + "*}")
                .count(100)
                .build();
        int outboxCount=0;
        int failures=0;
        //第一层扫描：寻找各张优惠劵的Outbox Key
        try (Cursor<String> keys = stringRedisTemplate.scan(options)){
            while (keys.hasNext()){
                String outboxKey = keys.next();
                try {
                    // 例如：
                    // m1kasa:seckill:outbox:{10}
                    // 提取出其中的 "10"
                    String idText = outboxKey.substring(
                            SeckillRedisKeys.OUTBOX_PREFIX.length(),
                            outboxKey.length() - 1
                    );
                    Long voucherId=Long.valueOf(idText);

                    // 校验 ID 和完整 Key，避免处理格式异常的数据
                    if (voucherId <= 0
                            || !outboxKey.equals(
                            SeckillRedisKeys.outbox(voucherId)
                    )) {
                        throw new IllegalArgumentException(
                                "秒杀 Outbox Key 格式不合法"
                        );
                    }
                    outboxCount++;
                    // 第二层扫描、批量投递继续复用原方法
                    failures += dispatchActivity(voucherId);
                }catch (RuntimeException ex) {
                    failures++;

                    log.warn(
                            "处理秒杀 Outbox 失败，key={}",
                            outboxKey,
                            ex
                    );
                }
            }
        }catch (RuntimeException ex) {
            failures++;

            log.warn("扫描秒杀 Outbox Key 失败", ex);
        }

        XxlJobHelper.log(
                "Outbox 扫描结束，处理 Outbox 次数={}，失败项数={}",
                outboxCount,
                failures
        );
        if (failures>0){
            XxlJobHelper.handleFail(
                    "本轮存在投递或扫描失败，失败项数=" + failures
            );
        }
    }
    private static final int BATCH_SIZE = 100;
    //扫描某张劵的Outbox,按批提交投递
    private int dispatchActivity(Long voucherId) {
        String outboxKey = SeckillRedisKeys.outbox(voucherId);

        ScanOptions options = ScanOptions.scanOptions()
                .count(100)
                .build();
        //这个列表用来保存一批投递任务的 Future。
        List<CompletableFuture<Boolean>> batch =
                new ArrayList<>(BATCH_SIZE);

        int failures = 0;

        try (Cursor<Map.Entry<String, String>> cursor =
                     stringRedisTemplate
                             .<String, String>opsForHash()
                             .scan(outboxKey, options)) {

            while (cursor.hasNext()) {
                Map.Entry<String, String> entry = cursor.next();

                try {
                    String requestId = entry.getKey();
                    Long userId = Long.valueOf(entry.getValue());

                    if (userId <= 0) {
                        throw new IllegalArgumentException(
                                "Outbox 中的用户 ID 不合法"
                        );
                    }

                    // 提交投递，当前这里不逐条等待
                    CompletableFuture<Boolean> attempt =
                            dispatcher.dispatchOne(
                                    voucherId,
                                    requestId,
                                    userId
                            ).handle((unused, error) -> {
                                if (error != null) {
                                    log.warn(
                                            "Outbox 补发失败，voucherId={}, requestId={}",
                                            voucherId,
                                            requestId,
                                            error
                                    );
                                    return false;
                                }

                                return true;
                            });

                    batch.add(attempt);

                } catch (RuntimeException ex) {
                    // 参数解析或调用过程直接抛出的异常
                    failures++;

                    log.warn(
                            "处理 Outbox 记录失败，voucherId={}, requestId={}",
                            voucherId,
                            entry.getKey(),
                            ex
                    );
                }

                // 凑够一批后，统一等待这一批结束
                if (batch.size() >= BATCH_SIZE) {
                    failures += awaitBatch(batch);
                }
            }

        } catch (RuntimeException ex) {
            // 扫描失败也计入本轮失败项
            failures++;

            log.warn(
                    "扫描 Outbox 失败，voucherId={}",
                    voucherId,
                    ex
            );
        }

        // 处理最后不足 100 条的部分；
        // 扫描中途失败时，也等待已经提交的这部分任务
        failures += awaitBatch(batch);

        return failures;
    }
    //等这一批尝试结束，统计失败数
    private int awaitBatch(
            List<CompletableFuture<Boolean>> batch
    ) {
        if (batch.isEmpty()) {
            return 0;
        }

        try {
            // 等这一批所有投递尝试结束
            CompletableFuture.allOf(
                    batch.toArray(new CompletableFuture<?>[0])
            ).join();

            int failures = 0;

            for (CompletableFuture<Boolean> attempt : batch) {
                // allOf 已经等完，这里只是读取结果
                if (!Boolean.TRUE.equals(attempt.join())) {
                    failures++;
                }
            }

            return failures;

        } finally {
            batch.clear();
        }
    }
}
