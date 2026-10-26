package com.m1kasa.m1kasavoucherservice.mq;
/*
* 单条outbox记录的投递方法，由定时任务批量调用它
* 构造消息->发送到Rabbitmq->发布检查通过后删除记录
* */

import com.m1kasa.m1kasavoucherservice.constant.SeckillRedisKeys;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class SeckillOutboxDispatcher {
    private final SeckillOrderPublisher publisher;
    private final StringRedisTemplate stringRedisTemplate;

    public SeckillOutboxDispatcher(
            SeckillOrderPublisher publisher,
            StringRedisTemplate stringRedisTemplate) {
        this.publisher = publisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public CompletableFuture<Void> dispatchOne(
            Long voucherId,
            String requestId,
            Long userId
    ){
        //使用Outbox中原有的请求ID，还原下单消息
        SeckillOrderCreateMessage message =
                new SeckillOrderCreateMessage(
                        requestId,
                        voucherId,
                        userId
                );
        //返回的future异常完成不会执行thenrun
        return publisher.send(message)
                .thenRun(()->{
                    // 发布检查通过后，删除这一条待发送记录
                    stringRedisTemplate.opsForHash().delete(
                            SeckillRedisKeys.outbox(voucherId),
                            requestId
                    );
                });
        //异常仍然会传递到 dispatchOne() 返回的 Future，后面的定时任务要接着处理。 如果发送成功、删除记录时 Redis 出错
        // ，这个 Future 也会异常完成，后续可能重复发送，需要订单消费者幂等兜底。
    }
}
