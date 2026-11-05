package com.m1kasa.m1kasavoucherservice.mq;

import com.m1kasa.voucher.api.constant.VoucherMqConstants;
import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillStockRejectedPublisher {
    private final RabbitTemplate rabbitTemplate;

    public SeckillStockRejectedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
        SeckillStockRejectedMessage message
    ){
        //每次发送尝试，使用新的发布确认标识
        CorrelationData correlationData=new CorrelationData(
                UUID.randomUUID().toString()
        );
        try {
            rabbitTemplate.convertAndSend(
                    VoucherMqConstants.EXCHANGE,
                    VoucherMqConstants.STOCK_REJECTED_ROUTING_KEY,
                    message,
                    amqbMessage -> {
                        //同一个拒绝事件重发时，消息标识保持不变
                        amqbMessage.getMessageProperties()
                                .setMessageId(
                                        "seckill-stock-rejected:"
                                        +message.requestId()
                                );
                        amqbMessage.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );
                        return amqbMessage;
                    },
                    correlationData
            );
        }catch (RuntimeException ex){
            return CompletableFuture.failedFuture(ex);
        }
        return correlationData.getFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    if (correlationData.getReturned()!=null){
                        throw new IllegalStateException("库存拒绝事件无法路由到队列");
                    }
                    if (!confirm.ack()){
                        throw new IllegalStateException(
                                "库存拒绝事件发布未获确认:"+confirm.reason()
                        );
                    }
                    return null;
                });
    }
}
