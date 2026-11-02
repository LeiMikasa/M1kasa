package com.m1kasa.m1kasacontentservice.mq;

import com.m1kasa.m1kasacontentservice.constant.BlogLikeMqConstants;
import com.m1kasa.m1kasacontentservice.service.BlogLikeDeadLetterService;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "m1kasa.blog.like.dead-letter",
        name = "enabled",
        havingValue = "true"
)
public class BlogLikeDeadLetterCollectJob {

    private static final Logger log =
            LoggerFactory.getLogger(
                    BlogLikeDeadLetterCollectJob.class
            );

    private static final int BATCH_SIZE = 20;

    private final RabbitTemplate rabbitTemplate;
    private final BlogLikeDeadLetterService deadLetterService;

    private final MessagePropertiesConverter propertiesConverter =
            new DefaultMessagePropertiesConverter();

    public BlogLikeDeadLetterCollectJob(
            RabbitTemplate rabbitTemplate,
            BlogLikeDeadLetterService deadLetterService
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.deadLetterService = deadLetterService;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void collect() {
        try {
            rabbitTemplate.execute(channel -> {
                for (int i = 0; i < BATCH_SIZE; i++) {
                    // false：不自动确认，保存成功后再手动ACK
                    GetResponse response = channel.basicGet(
                            BlogLikeMqConstants.DEAD_LETTER_QUEUE,
                            false
                    );

                    // 当前没有消息，结束本批
                    if (response == null) {
                        break;
                    }

                    long deliveryTag = response
                            .getEnvelope()
                            .getDeliveryTag();

                    try {
                        // RabbitMQ客户端属性转为Spring消息属性
                        MessageProperties properties =
                                propertiesConverter.toMessageProperties(
                                        response.getProps(),
                                        response.getEnvelope(),
                                        "UTF-8"
                                );

                        // 保留原始消息体
                        Message message = new Message(
                                response.getBody(),
                                properties
                        );

                        // 保存成功后，才继续执行ACK
                        deadLetterService.save(message);

                    } catch (RuntimeException ex) {
                        log.warn(
                                "点赞死信保存失败，重新入队，messageId={}",
                                response.getProps().getMessageId(),
                                ex
                        );

                        // 只拒绝当前消息，并重新放回死信队列
                        channel.basicNack(
                                deliveryTag,
                                false,
                                true
                        );

                        // 保存失败，结束本轮，等待下一轮再试
                        return null;
                    }

                    // 数据库保存成功，只确认当前消息
                    channel.basicAck(deliveryTag, false);
                }

                return null;
            });

        } catch (RuntimeException ex) {
            log.warn("采集点赞死信时发生异常", ex);
        }
    }
}
