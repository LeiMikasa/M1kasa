package com.m1kasa.m1kasavoucherservice.mq;

import com.m1kasa.m1kasavoucherservice.constant.SeckillMqConstants;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillOrderPublisher {

    private final RabbitTemplate rabbitTemplate;


    public SeckillOrderPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
            SeckillOrderCreateMessage message
    ){
        //每次发送使用独立的发布确认标识
        CorrelationData correlationData=new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.convertAndSend(
                    SeckillMqConstants.EXCHANGE,
                    SeckillMqConstants.ORDER_ROUTING_KEY,
                    message,
                    //发送前加工消息
                    amqpMessage->{
                        //业务消息标识，重试发送时保持不变
                        //把业务请求ID写进消息属性，方便后续追踪和识别消息
                        amqpMessage.getMessageProperties()
                                .setMessageId(
                                        message.requestId()
                                );

                        //将消息标记为持久化消息
                        amqpMessage.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );
                        return amqpMessage;
                    },
                    correlationData
            );
        }catch (RuntimeException ex){
            //将同步发送异常也转换为失败的future
            return CompletableFuture.failedFuture(ex);
        }
        //future是一个结构容器，现在结构可能还没到，之后会变成成功或失败
        //这里的future保存的是rabbitmq的确认结果
        //Confirm主要有:
        // confirm.ack() 是否确认 confirm.reason() 未确认时的原因，可能为空
        return correlationData.getFuture()
                // 表示如果这个Future在5秒内没有完成，就让它以超时异常完成
                .orTimeout(5, TimeUnit.SECONDS)
                //确认到达后，检查结果
                .thenApply(confirm -> {
                    //检查是否退回
                    //即使交换机确认了消息，也可能没有匹配的队列
                    if (correlationData.getReturned()!=null){
                        throw new IllegalStateException(
                                "订单消息无法路由到队列"
                        );
                    }
                    //检查是否ack
                    if (!confirm.ack()){
                        throw new IllegalStateException(
                                "订单消息发布未获确认: "+
                                        confirm.reason()
                        );
                    }
                    return null;
                });


    }

}
