package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.m1kasaorderservice.entity.MqDeadLetter;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
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
public class OrderDeadLetterReplayPublisher {
    private static final String SOURCE_QUEUE=
            "m1kasa.seckill.order.queue";

    private static final String EXCHANGE=
            "m1kasa.seckill.exchange";

    private static final String ROUTING_KEY=
            "seckill.order.create";

    private final RabbitTemplate rabbitTemplate;

    public OrderDeadLetterReplayPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public CompletableFuture<Void> send(MqDeadLetter record){
        //根据已知来源限定重发
        if (record==null || !SOURCE_QUEUE.equals(record.getSourceQueue())){
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "仅支持订单创建消息重发"
                    )
            );
        }
        //每次发送尝试，使用新的发布确认ID
        CorrelationData correlationData=new CorrelationData(
                UUID.randomUUID().toString()
        );
        try {
            //恢复原始消息字节
            byte[] body= Base64.getDecoder()
                    .decode(record.getBodyBase64());
            MessageProperties properties=new MessageProperties();
            //保持原来的消息ID
            properties.setMessageId(record.getMessageId());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            //原记录缺少内容类型或编码时，使用项目JSON消息的默认值
            properties.setContentType(
                    StringUtils.hasText(record.getContentType())
                    ?record.getContentType():MessageProperties.CONTENT_TYPE_JSON
            );
            properties.setContentEncoding(
                    StringUtils.hasText(record.getContentEncoding())
                            ? record.getContentEncoding()
                            : "UTF-8"
            );

            // 按当前业务队列对应的消息类型设置类型头
            properties.setHeader(
                    "__TypeId__",
                    SeckillOrderCreateMessage.class.getName()
            );

            Message message = new Message(body, properties);

            rabbitTemplate.send(
                    EXCHANGE,
                    ROUTING_KEY,
                    message,
                    correlationData
            );

        }catch (RuntimeException ex){
            return CompletableFuture.failedFuture(ex);
        }
        return correlationData.getFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(confirm -> {
                    if (correlationData.getReturned() != null) {
                        throw new IllegalStateException(
                                "重发消息无法路由到业务队列"
                        );
                    }

                    if (!confirm.ack()) {
                        throw new IllegalStateException(
                                "重发消息未获发布确认："
                                        + confirm.reason()
                        );
                    }

                    return null;
                });
    }
}
