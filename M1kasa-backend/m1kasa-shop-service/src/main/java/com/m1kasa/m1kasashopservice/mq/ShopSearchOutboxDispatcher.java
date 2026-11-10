package com.m1kasa.m1kasashopservice.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasashopservice.entity.ShopSearchOutbox;
import com.m1kasa.m1kasashopservice.mapper.ShopSearchOutboxMapper;
import com.m1kasa.shop.api.message.ShopSearchSyncMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Component
public class ShopSearchOutboxDispatcher {

    // 单实例最多保留 100 个尚未完成的投递任务
    private final Semaphore inFlight = new Semaphore(100);

    private final ShopSearchPublisher publisher;
    private final ShopSearchOutboxMapper outboxMapper;
    private final JsonMapper jsonMapper;
    private final Executor executor;

    public ShopSearchOutboxDispatcher(
            ShopSearchPublisher publisher,
            ShopSearchOutboxMapper outboxMapper,
            JsonMapper jsonMapper,
            @Qualifier("shopSearchOutboxExecutor")
            Executor executor
    ) {
        this.publisher = publisher;
        this.outboxMapper = outboxMapper;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    public CompletableFuture<Void> dispatchOne(
            ShopSearchOutbox record
    ) {
        if (record == null || record.getId() == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Outbox 记录不合法")
            );
        }

        // 已经发送成功的记录，直接结束
        if (Integer.valueOf(1).equals(record.getStatus())) {
            return CompletableFuture.completedFuture(null);
        }

        if (!Integer.valueOf(0).equals(record.getStatus())) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Outbox 状态不合法")
            );
        }

        // 取得投递名额，不等待、不阻塞当前线程
        if (!inFlight.tryAcquire()) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "店铺搜索投递窗口已满，等待后续补发"
                    )
            );
        }

        try {
            CompletableFuture<Void> task =
                    CompletableFuture.completedFuture(record)

                            // 在线程池中读取快照并调用发布器
                            .thenComposeAsync(row -> {

                                ShopSearchSyncMessage message =
                                        jsonMapper.readValue(
                                                row.getPayload(),
                                                ShopSearchSyncMessage.class
                                        );

                                // 使用保存好的消息，沿用原消息 ID 和版本
                                return publisher.send(message);

                            }, executor)

                            // 发布检查通过后，再在线程池中更新状态
                            .thenRunAsync(
                                    () -> markPublished(record.getId()),
                                    executor
                            );

            // 成功或失败，都归还投递名额
            return task.whenComplete(
                    (unused, error) -> inFlight.release()
            );

        } catch (RuntimeException ex) {
            // 提交异步任务时直接抛异常，也要释放名额
            inFlight.release();

            return CompletableFuture.failedFuture(ex);
        }
    }

    private void markPublished(Long id) {

        int updated = outboxMapper.update(
                null,
                new LambdaUpdateWrapper<ShopSearchOutbox>()
                        .eq(ShopSearchOutbox::getId, id)
                        .eq(ShopSearchOutbox::getStatus, 0)
                        .set(ShopSearchOutbox::getStatus, 1)
                        .set(
                                ShopSearchOutbox::getUpdateTime,
                                LocalDateTime.now()
                        )
        );

        if (updated == 1) {
            return;
        }

        // 可能另一条并发投递已经把状态更新成了 1
        ShopSearchOutbox latest = outboxMapper.selectById(id);

        if (latest != null
                && Integer.valueOf(1).equals(latest.getStatus())) {
            return;
        }

        throw new IllegalStateException(
                "更新店铺搜索 Outbox 状态失败，id=" + id
        );
    }
}
