package com.m1kasa.m1kasapayservice.mq;

import com.m1kasa.pay.api.constant.PaymentMqConstants;
import com.m1kasa.pay.api.message.PaymentSuccessMessage;
import org.springframework.amqp.core.Correlation;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentSuccessPublisher {
    private final RabbitTemplate rabbitTemplate;

    public PaymentSuccessPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
        PaymentSuccessMessage message
    ){
        if (message==null
        ||message.eventId()==null
        ||message.eventId().isBlank()){
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("支付成功事件ID不能为空")
            );
        }
        CorrelationData correlation = new CorrelationData(
                UUID.randomUUID().toString()
        );
        try {
            rabbitTemplate.convertAndSend(
                    PaymentMqConstants.EXCHANGE,
                    PaymentMqConstants.SUCCESS_ROUTING_KEY,
                    message,
                    amqpMessage->{
                        amqpMessage.getMessageProperties()
                                .setMessageId(message.eventId());
                        amqpMessage.getMessageProperties()
                                .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return amqpMessage;
                    },
                    correlation
            );
        }catch (RuntimeException ex){
            return CompletableFuture.failedFuture(ex);
        }
        return correlation.getFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    if (correlation.getReturned()!=null){
                        throw new IllegalStateException("支付成功事件无法路由到队列");
                    }
                    if (!confirm.ack()){
                        throw new IllegalStateException(
                                "支付成功事件未发布未获确认："
                                +confirm.reason()
                        );
                    }
                    return null;
                });
    }
}
