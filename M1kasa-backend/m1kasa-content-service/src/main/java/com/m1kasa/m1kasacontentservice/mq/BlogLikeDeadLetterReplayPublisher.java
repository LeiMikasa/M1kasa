package com.m1kasa.m1kasacontentservice.mq;

import com.m1kasa.content.api.message.BlogLikeChangedMessage;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeMqConstants;
import com.m1kasa.m1kasacontentservice.entity.BlogLikeDeadLetter;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class BlogLikeDeadLetterReplayPublisher {
    private final RabbitTemplate rabbitTemplate;

    public BlogLikeDeadLetterReplayPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(
        BlogLikeDeadLetter record
    ){
        //限定为点赞业务队列的消息
        if (record==null || !BlogLikeMqConstants.QUEUE.equals(record.getSourceQueue())){
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("仅支持点赞变更消息重发")
            );
        }
        //每次发送使用新的发布确认标识
        CorrelationData correlationData=new CorrelationData(
                UUID.randomUUID().toString()
        );

        try {
            if (record.getBodyBase64()==null){
                throw new IllegalStateException("死信记录缺少原始消息体");
            }
            //1.恢复原始消息字节
            byte[] body = Base64.getDecoder().decode(record.getBodyBase64());
            //2.创建本次发送使用的消息属性
            MessageProperties properties=new MessageProperties();
            //保留原消息ID
            properties.setMessageId(record.getMessageId());
            properties.setDeliveryMode(
                    MessageDeliveryMode.PERSISTENT
            );
            // 缺失时使用当前业务的JSON默认值
            properties.setContentType(
                    StringUtils.hasText(record.getContentType())
                            ? record.getContentType()
                            : MessageProperties.CONTENT_TYPE_JSON
            );

            properties.setContentEncoding(
                    StringUtils.hasText(record.getContentEncoding())
                            ? record.getContentEncoding()
                            : "UTF-8"
            );
            //3.设置当前业务消息的类型头
            properties.setHeader(
                    "__TypeId__",
                    BlogLikeChangedMessage.class.getName()
            );
            Message message=new Message(body,properties);
            //4.将原始消息重新发送到业务交换机
            rabbitTemplate.send(
                    BlogLikeMqConstants.EXCHANGE,
                    BlogLikeMqConstants.ROUTING_KEY,
                    message,
                    correlationData
            );

        }catch (RuntimeException ex){
            return CompletableFuture.failedFuture(ex);
        }
        //5.检查发布确认与消息退回
        return correlationData.getFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    if (correlationData.getReturned()!=null){
                         throw new IllegalStateException("点赞重发消息无法路由到业务队列");
                    }
                    if (!confirm.ack()){
                        throw new IllegalStateException("点赞重发消息未获发布确认:" + confirm.reason());
                    }
                    return null;
                });
    }

}
