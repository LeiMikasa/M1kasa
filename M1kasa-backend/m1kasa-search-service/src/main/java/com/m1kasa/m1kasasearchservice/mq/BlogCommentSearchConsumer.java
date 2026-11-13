package com.m1kasa.m1kasasearchservice.mq;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.m1kasa.content.api.message.BlogCommentSearchMessage;
import com.m1kasa.m1kasasearchservice.embedding.SiliconFlowEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class BlogCommentSearchConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(BlogCommentSearchConsumer.class);

    private static final String INDEX = "m1kasa_shop_review_v2";

    public static final String DLX = "m1kasa.blog.comment.search.dlx";
    public static final String DLQ = "m1kasa.blog.comment.search.dlq";
    public static final String DLQ_ROUTING_KEY = "blog.comment.search.failed";

    private final ElasticsearchClient elasticsearchClient;
    private final SiliconFlowEmbeddingService embeddingService;

    public BlogCommentSearchConsumer(
            ElasticsearchClient elasticsearchClient,
            SiliconFlowEmbeddingService embeddingService
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingService = embeddingService;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = BlogCommentSearchMessage.QUEUE,
                    durable = "true",
                    arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = DLX),
                            @Argument(name = "x-dead-letter-routing-key", value = DLQ_ROUTING_KEY)
                    }
            ),
            exchange = @Exchange(
                    value = BlogCommentSearchMessage.EXCHANGE,
                    type = ExchangeTypes.DIRECT,
                    durable = "true"
            ),
            key = BlogCommentSearchMessage.ROUTING_KEY
    ))
    public void handle(BlogCommentSearchMessage message) throws IOException {
        String id = message.commentId().toString();

        // 评论已经写入过，跳过本次向量化
        if (elasticsearchClient.exists(request -> request.index(INDEX).id(id)).value()) {
            return;
        }

        float[] embedding = embeddingService.embed(message.content());
        Map<String, Object> document = Map.of(
                "commentId", id,
                "blogId", message.blogId().toString(),
                "shopId", message.shopId().toString(),
                "content", message.content(),
                "embedding", embedding
        );

        try {
            elasticsearchClient.create(request -> request
                    .index(INDEX)
                    .id(id)
                    .document(document));
        } catch (ElasticsearchException ex) {
            // 并发处理同一条评论时，另一条投递可能已经写入成功
            if (ex.status() == 409
                    && "version_conflict_engine_exception".equals(ex.error().type())) {
                return;
            }
            throw ex;
        }

        log.info("评论向量已写入 ES，commentId={}", id);
    }
}
