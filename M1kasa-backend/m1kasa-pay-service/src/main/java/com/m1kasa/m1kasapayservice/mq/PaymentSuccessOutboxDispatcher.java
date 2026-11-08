package com.m1kasa.m1kasapayservice.mq;


import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasapayservice.entity.PaymentSuccessOutbox;
import com.m1kasa.m1kasapayservice.mapper.PaymentSuccessOutboxMapper;
import com.m1kasa.pay.api.message.PaymentSuccessMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Component
public class PaymentSuccessOutboxDispatcher {
    //限制尚未完成的投递流程数量，不阻塞等待名额
    private final Semaphore inFlight=new Semaphore(100);

    private final PaymentSuccessOutboxMapper outboxMapper;
    private final PaymentSuccessPublisher publisher;
    private final Executor executor;

    public PaymentSuccessOutboxDispatcher(PaymentSuccessOutboxMapper outboxMapper,
                                          PaymentSuccessPublisher publisher,
                                          @Qualifier("paymentOutboxExecutor") Executor executor) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
        this.executor = executor;
    }
    public CompletableFuture<Void> dispatchOne(Long outboxId){
        if (outboxId==null||outboxId<=0){
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Outbox ID不合法")
            );
        }
        if (!inFlight.tryAcquire()){
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("支付事件投递窗口已满")
            );
        }

        try {
            CompletableFuture<Void> task = CompletableFuture.completedFuture(outboxId)
                    .thenComposeAsync(id -> {
                        PaymentSuccessOutbox record = outboxMapper.selectById(id);
                        if (record == null) {
                            throw new IllegalStateException("支付Outbox不存在");
                        }
                        if (Objects.equals(
                                record.getStatus(), 1
                        )) {
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        if (!Objects.equals(
                                record.getStatus(), 0
                        )) {
                            throw new IllegalStateException(
                                    "支付Outbox状态不合法"
                            );
                        }
                        PaymentSuccessMessage message = new PaymentSuccessMessage(
                                record.getEventId(),
                                record.getPayNo(),
                                record.getOrderId(),
                                record.getUserId(),
                                record.getPayAmountFen(),
                                record.getChannel(),
                                record.getChannelTradeNo(),
                                record.getPaidTime()
                        );
                        return publisher.send(message)
                                .thenRunAsync(
                                        () -> markSent(id),
                                        executor
                                );
                    }, executor);

            return task.whenComplete(
                    (unused, error) -> inFlight.release()
            );
        }catch (RuntimeException ex){
            inFlight.release();
            return CompletableFuture.failedFuture(ex);
        }
    }
    private void markSent(Long outboxId) {
        int updated = outboxMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentSuccessOutbox>()
                        .eq(PaymentSuccessOutbox::getId, outboxId)
                        .eq(PaymentSuccessOutbox::getStatus, 0)
                        .set(PaymentSuccessOutbox::getStatus, 1)
                        .set(
                                PaymentSuccessOutbox::getUpdateTime,
                                LocalDateTime.now()
                        )
        );

        if (updated == 1) {
            return;
        }

        // 可能有另一次投递已经将它标记为成功
        PaymentSuccessOutbox latest =
                outboxMapper.selectById(outboxId);

        if (latest == null
                || !Objects.equals(latest.getStatus(), 1)) {
            throw new IllegalStateException(
                    "更新支付Outbox发送状态失败"
            );
        }
    }
}
