package com.m1kasa.m1kasavoucherservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasavoucherservice.constant.SeckillRedisKeys;
import com.m1kasa.m1kasavoucherservice.entity.SeckillVoucher;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillVoucherMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOutboxDispatcher dispatcher;

    public SeckillOutboxJob(
            SeckillVoucherMapper seckillVoucherMapper,
            StringRedisTemplate stringRedisTemplate,
            SeckillOutboxDispatcher dispatcher
    ) {
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void dispatchPending() {
        // 查询活动 ID，包含已经结束的活动
        List<SeckillVoucher> activities =
                seckillVoucherMapper.selectList(
                        new LambdaQueryWrapper<SeckillVoucher>()
                                .select(SeckillVoucher::getVoucherId)
                );

        for (SeckillVoucher activity : activities) {
            dispatchActivity(activity.getVoucherId());
        }
    }

    private void dispatchActivity(Long voucherId) {
        String outboxKey = SeckillRedisKeys.outbox(voucherId);
        //设置扫描配置，提示redis每次扫描时处理大约多少数据
        ScanOptions options = ScanOptions.scanOptions()
                .count(100)
                .build();

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

                    // 后台任务等待这一条消息的发送、确认和清理完成
                    dispatcher.dispatchOne(
                            voucherId,
                            requestId,
                            userId
                    ).join();

                } catch (RuntimeException ex) {
                    // 不删除失败记录，后续继续重试
                    log.warn(
                            "Outbox 投递失败，voucherId={}, requestId={}",
                            voucherId,
                            entry.getKey(),
                            ex
                    );
                }
            }

        } catch (RuntimeException ex) {
            // 一个活动扫描失败，不影响后面的活动
            log.warn(
                    "扫描 Outbox 失败，voucherId={}",
                    voucherId,
                    ex
            );
        }
    }
}
