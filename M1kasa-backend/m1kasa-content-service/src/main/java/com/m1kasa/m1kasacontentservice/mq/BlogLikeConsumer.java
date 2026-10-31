package com.m1kasa.m1kasacontentservice.mq;

import com.m1kasa.content.api.message.BlogLikeChangedMessage;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeMqConstants;
import com.m1kasa.m1kasacontentservice.service.BlogLikeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BlogLikeConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(BlogLikeConsumer.class);

    private final BlogLikeSyncService syncService;

    public BlogLikeConsumer(BlogLikeSyncService syncService) {
        this.syncService = syncService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = BlogLikeMqConstants.QUEUE,
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = BlogLikeMqConstants
                                                    .DEAD_LETTER_EXCHANGE
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = BlogLikeMqConstants
                                                    .DEAD_LETTER_ROUTING_KEY
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = BlogLikeMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = BlogLikeMqConstants.ROUTING_KEY
            )
    )
    public void handle(BlogLikeChangedMessage message) {
        // 通过独立 Service 调用，完成数据库事务
        syncService.sync(message);

        // Service 正常返回时，事务已经提交
        log.info(
                "点赞消息处理完成，blogId={}, userId={}, version={}",
                message.blogId(),
                message.userId(),
                message.version()
        );
    }
}
