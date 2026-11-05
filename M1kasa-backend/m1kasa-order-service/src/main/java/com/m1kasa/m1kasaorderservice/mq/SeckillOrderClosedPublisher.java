package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.order.api.constant.OrderMqConstants;
import com.m1kasa.order.api.message.SeckillOrderClosedMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillOrderClosedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SeckillOrderClosedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(SeckillOrderClosedMessage message) {
        // 业务消息 ID 稳定；每次发送尝试的发布确认 ID 独立生成。
        CorrelationData correlationData =
                new CorrelationData(UUID.randomUUID().toString());

        try {
            rabbitTemplate.convertAndSend(
                    OrderMqConstants.EXCHANGE,
                    OrderMqConstants.SECKILL_ORDER_CLOSED_ROUTING_KEY,
                    message,
                    amqpMessage -> {
                        amqpMessage.getMessageProperties().setMessageId(
                                "seckill-order-closed:" + message.requestId()
                        );
                        amqpMessage.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return amqpMessage;
                    },
                    correlationData
            );
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }

        return correlationData.getFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    if (correlationData.getReturned() != null) {
                        throw new IllegalStateException("订单关闭事件无法路由到队列");
                    }
                    if (!confirm.ack()) {
                        throw new IllegalStateException(
                                "订单关闭事件发布未获确认：" + confirm.reason()
                        );
                    }
                    return null;
                });
    }
}
