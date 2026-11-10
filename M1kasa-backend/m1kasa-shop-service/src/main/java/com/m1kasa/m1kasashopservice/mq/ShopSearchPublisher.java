package com.m1kasa.m1kasashopservice.mq;

import com.m1kasa.shop.api.constant.ShopSearchMqConstants;
import com.m1kasa.shop.api.message.ShopSearchSyncMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ShopSearchPublisher {
    private final RabbitTemplate rabbitTemplate;

    public ShopSearchPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
        ShopSearchSyncMessage message
    ){
        //每次发送尝试，都使用独立的发布确认ID
        CorrelationData correlationData = new CorrelationData(
                UUID.randomUUID().toString()
        );
        try {
            rabbitTemplate.convertAndSend(
                    ShopSearchMqConstants.EXCHANGE,
                    ShopSearchMqConstants.ROUTING_KEY,
                    message,
                    amqbMessage->{
                        amqbMessage.getMessageProperties()
                                .setMessageId(message.requestId());
                        amqbMessage.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return amqbMessage;
                    },
                    correlationData
            );
        }catch (RuntimeException ex){
            return CompletableFuture.failedFuture(ex);
        }
        return correlationData.getFuture()
                // 发送调用返回后，最多等待 5 秒发布确认
                .orTimeout(5, TimeUnit.SECONDS)

                // 收到发布确认后，检查是否真正通过发布检查
                .thenAccept(confirm -> {

                    // 收到 ack，也可能因为没有匹配队列而被退回
                    if (correlationData.getReturned() != null) {
                        throw new IllegalStateException(
                                "店铺搜索同步消息无法路由到队列"
                        );
                    }

                    if (!confirm.ack()) {
                        throw new IllegalStateException(
                                "店铺搜索同步消息发布未获确认："
                                        + confirm.reason()
                        );
                    }
                });
    }
}
