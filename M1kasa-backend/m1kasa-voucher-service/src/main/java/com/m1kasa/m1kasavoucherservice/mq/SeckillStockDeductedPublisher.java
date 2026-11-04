package com.m1kasa.m1kasavoucherservice.mq;

import com.m1kasa.voucher.api.constant.VoucherMqConstants;
import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillStockDeductedPublisher {
    private final RabbitTemplate rabbitTemplate;

    public SeckillStockDeductedPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
        SeckillStockDeductedMessage message
    ){
        //每次发送尝试，使用独立的发布确认标识
        CorrelationData correlationData=new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(
                    VoucherMqConstants.EXCHANGE,
                    VoucherMqConstants.STOCK_DEDUCTED_ROUTING_KEY,
                    message,
                    amqpMessage->{
                        //同一个库存成功事件重发时，此值保持不变
                        amqpMessage.getMessageProperties()
                                .setMessageId("seckill-stock-deducted:"
                                        + message.requestId());
                        //标记为持久化消息
                        amqpMessage.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return amqpMessage;
                    },
                    correlationData
            );
        }catch (RuntimeException ex){
            //同步发送异常也转换为失败的Future
            return CompletableFuture.failedFuture(ex);
        }
        return correlationData.getFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    // 交换机收到消息，不代表一定能路由到队列
                    if (correlationData.getReturned() != null) {
                        throw new IllegalStateException(
                                "库存扣减成功事件无法路由到队列"
                        );
                    }

                    if (!confirm.ack()) {
                        throw new IllegalStateException(
                                "库存扣减成功事件发布未获确认："
                                        + confirm.reason()
                        );
                    }

                    return null;
                });
    }
}
