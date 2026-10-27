package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.order.api.constant.OrderMqConstants;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillOrderCreatedPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SeckillOrderCreatedPublisher(
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
            SeckillOrderCreatedMessage message
    ) {
        // 每一次发送尝试，使用一个新的确认标识
        CorrelationData correlationData = new CorrelationData(
                UUID.randomUUID().toString()
        );

        try {
            rabbitTemplate.convertAndSend(
                    OrderMqConstants.EXCHANGE,
                    OrderMqConstants.SECKILL_ORDER_CREATED_ROUTING_KEY,
                    message,
                    amqpMessage -> {
                        // 同一条业务事件重发时，消息标识保持不变
                        amqpMessage.getMessageProperties()
                                .setMessageId(
                                        "seckill-order-created:"
                                                + message.requestId()
                                );

                        amqpMessage.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );

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
                        throw new IllegalStateException(
                                "订单创建事件无法路由到队列"
                        );
                    }

                    if (!confirm.ack()) {
                        throw new IllegalStateException(
                                "订单创建事件发布未获确认："
                                        + confirm.reason()
                        );
                    }

                    return null;
                });
    }
}
