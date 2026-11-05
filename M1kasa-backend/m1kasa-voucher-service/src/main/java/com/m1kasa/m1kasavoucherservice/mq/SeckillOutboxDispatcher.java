package com.m1kasa.m1kasavoucherservice.mq;

import com.m1kasa.m1kasavoucherservice.constant.SeckillRedisKeys;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Component
public class SeckillOutboxDispatcher {

    // 单实例最多允许 100 个尚未完成的投递任务
    private static final int MAX_IN_FLIGHT = 100;

    private final Semaphore inFlight =
            new Semaphore(MAX_IN_FLIGHT);

    private final SeckillOrderPublisher publisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final Executor outboxExecutor;

    public SeckillOutboxDispatcher(
            SeckillOrderPublisher publisher,
            StringRedisTemplate stringRedisTemplate,
            @Qualifier("seckillOutboxExecutor")
            Executor outboxExecutor
    ) {
        this.publisher = publisher;
        this.stringRedisTemplate = stringRedisTemplate;
        this.outboxExecutor = outboxExecutor;
    }

    public CompletableFuture<Void> dispatchOne(
            Long voucherId,
            String requestId,
            Long userId
    ) {
        // 尝试取得一个投递名额，不阻塞当前线程
        if (!inFlight.tryAcquire()) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "投递窗口已满，保留 Outbox 等待补发"
                    )
            );
        }

        try {
            // 沿用原请求 ID，重试时不能重新生成
            SeckillOrderCreateMessage message =
                    new SeckillOrderCreateMessage(
                            requestId,
                            voucherId,
                            userId
                    );

            CompletableFuture<Void> task =
                    CompletableFuture.completedFuture(message)

                            // 在线程池中发送，并跟随发布确认结果
                            .thenComposeAsync(
                                    msg -> publisher.send(msg),
                                    outboxExecutor
                            )

                            // 只有发布检查通过，才执行清理
                            .thenRunAsync(
                                    () -> {
                                        stringRedisTemplate
                                                .opsForHash()
                                                .delete(
                                                        SeckillRedisKeys.outbox(
                                                                voucherId
                                                        ),
                                                        requestId
                                                );
                                    },
                                    outboxExecutor
                            );

            // 成功或失败，都归还投递名额
            return task.whenComplete(
                    (unused, error) -> inFlight.release()
            );

        } catch (RuntimeException ex) {
            // 如果构造或提交异步链时直接抛异常，也要归还名额
            inFlight.release();

            return CompletableFuture.failedFuture(ex);
        }
    }
}
