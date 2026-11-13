package com.m1kasa.m1kasacontentservice.mq;

import com.m1kasa.content.api.message.BlogCommentSearchMessage;
import com.m1kasa.m1kasacontentservice.mapper.BlogCommentSearchOutboxMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(
        name = "m1kasa.blog.comment.search.outbox.enabled",
        havingValue = "true"
)
public class BlogCommentSearchOutboxJob {

    private final BlogCommentSearchOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    public BlogCommentSearchOutboxJob(
            BlogCommentSearchOutboxMapper outboxMapper,
            RabbitTemplate rabbitTemplate
    ) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @XxlJob("blogCommentSearchOutboxRetry")
    public void dispatchPending() {
        int published = 0;
        int failed = 0;

        for (BlogCommentSearchMessage message : outboxMapper.findPending()) {
            try {
                publish(message);
                outboxMapper.markPublished(message.commentId());
                published++;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                XxlJobHelper.handleFail("评论 Outbox 投递任务被中断");
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException ex) {
                failed++;
                XxlJobHelper.log("评论投递失败，commentId={}", message.commentId());
                XxlJobHelper.log(ex);
            }
        }

        String summary = "发布通过 " + published + " 条，失败 " + failed + " 条";
        XxlJobHelper.log(summary);
        if (failed > 0) {
            XxlJobHelper.handleFail(summary);
        }
    }

    private void publish(BlogCommentSearchMessage message)
            throws InterruptedException, ExecutionException, TimeoutException {
        var correlation = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(
                BlogCommentSearchMessage.EXCHANGE,
                BlogCommentSearchMessage.ROUTING_KEY,
                message,
                amqpMessage -> {
                    var properties = amqpMessage.getMessageProperties();
                    properties.setMessageId("blog-comment-search:" + message.commentId());
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return amqpMessage;
                },
                correlation
        );

        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (correlation.getReturned() != null) {
            throw new IllegalStateException("评论索引消息无法路由到队列");
        }
        if (!confirm.ack()) {
            throw new IllegalStateException("评论索引消息未获发布确认：" + confirm.reason());
        }
    }
}
