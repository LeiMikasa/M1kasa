package com.m1kasa.m1kasasearchservice.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasasearchservice.embedding.SiliconFlowEmbeddingService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@Profile("local")
@RestController
public class ShopReviewImportController {
    private static final String SOURCE_INDEX="m1kasa_shop_review_v1";
    private static final String TARGET_INDEX="m1kasa_shop_review_v2";

    private final ElasticsearchClient elasticsearchClient;
    private final SiliconFlowEmbeddingService embeddingService;

    public ShopReviewImportController(ElasticsearchClient elasticsearchClient, SiliconFlowEmbeddingService embeddingService) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingService = embeddingService;
    }
    @PostMapping("/internal/search/reviews/{commentId}/reindex")
    public Result<String> reindex(@PathVariable("commentId") long commentId) throws IOException {
        if (commentId<=0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,"评论ID必须大于0"
            );
        }
        String id=Long.toString(commentId);
        GetResponse<ReviewSource> oldDocument = elasticsearchClient.
                get(get -> get.index(SOURCE_INDEX)
                                .id(id)
                                .sourceIncludes("blogId", "shopId", "content"),
                        ReviewSource.class);

        ReviewSource review = oldDocument.source();
        if (!oldDocument.found()||review==null){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,"旧索引中找不到这条评论"
            );
        }
        float[] embedding = embeddingService.embed(review.content());
        var result=elasticsearchClient.index(index->index
                .index(TARGET_INDEX)
                .id(id)
                .refresh(Refresh.WaitFor)
                .document(Map.of(
                        "commentId", id,
                        "blogId", review.blogId(),
                        "shopId", review.shopId(),
                        "content", review.content(),
                        "embedding", embedding
                ))
        );
        return Result.ok(result.result().jsonValue());
    }

    public record ReviewSource(String blogId,String shopId,String content){

    }
}
