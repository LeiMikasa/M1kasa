package com.m1kasa.m1kasacontentservice.mq;


import com.m1kasa.content.api.message.BlogLikeChangedMessage;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeMqConstants;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class BlogLikePublisher {
    private final RabbitTemplate rabbitTemplate;

    public BlogLikePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(BlogLikeChangedMessage message){
        //基础参数校验，失败也通过Future返回
        if (message == null
                || message.blogId() == null
                || message.blogId() <= 0
                || message.userId() == null
                || message.userId() <= 0
                || message.liked() == null
                || message.version() == null
                || message.version() <= 0
                || message.occurredAt() == null
                || message.occurredAt() <= 0) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("点赞消息参数不合法")
                );
        }
        //业务消息ID：同一个用户，同一篇笔记，同一个版本保持不变
        String messageId="blog-like:"+message.blogId()+":"+message.userId()+":"+message.version();
        //发布确认ID：每次发送使用新的标识
        CorrelationData correlationData=new CorrelationData(UUID.randomUUID().toString());
        try {
              rabbitTemplate.convertAndSend(
                      BlogLikeMqConstants.EXCHANGE,
                      BlogLikeMqConstants.ROUTING_KEY,
                      message,
                      amqbmessage ->{
                          //设置业务消息标识
                          amqbmessage.getMessageProperties()
                                  .setMessageId(messageId);

                          //标记为持久化消息
                          amqbmessage.getMessageProperties()
                                  .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                          return amqbmessage;
                      },
                      correlationData
              );
        }catch (RuntimeException ex){
            //发送过程中累计发生的异常，转换为失败的Future
            return CompletableFuture.failedFuture(ex);
        }
        return correlationData.getFuture()
                //最多等5秒获取发布确认
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    //即使收到ack,也要检查消息是否因为无法路由被退回
                    if (correlationData.getReturned()!=null){
                        throw new IllegalStateException("点赞消息无法路由到队列，messageId="+messageId);
                    }

                    //RabbitMq未确认接收
                    if (!confirm.ack()){
                        throw new IllegalStateException("点赞消息发布未获确认，messageId="+messageId+
                                ",原因="+confirm.reason());
                    }
                    return null;
                });
    }

}
