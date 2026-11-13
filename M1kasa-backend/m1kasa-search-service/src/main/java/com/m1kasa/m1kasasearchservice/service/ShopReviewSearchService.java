package com.m1kasa.m1kasasearchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.m1kasa.search.api.dto.ReviewHit;
import com.m1kasa.m1kasasearchservice.embedding.SiliconFlowEmbeddingService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ShopReviewSearchService {

    private static final String INDEX = "m1kasa_shop_review_v2";
    private static final int TOP_K = 3;

    private final ElasticsearchClient elasticsearchClient;
    private final SiliconFlowEmbeddingService embeddingService;

    public ShopReviewSearchService(
            ElasticsearchClient elasticsearchClient,
            SiliconFlowEmbeddingService embeddingService
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingService = embeddingService;
    }

    public List<ReviewHit> search(String question, List<Long> shopIds) throws IOException {
        float[] embedding = embeddingService.embed(question);
        List<Float> queryVector = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            queryVector.add(value);
        }

        List<FieldValue> shopFilter = shopIds.stream()
                .distinct()
                .map(id -> FieldValue.of(id.toString()))
                .toList();

        var response = elasticsearchClient.search(search -> search
                        .index(INDEX)
                        .size(TOP_K)
                        .allowPartialSearchResults(false)
                        .source(source -> source.filter(fields ->
                                fields.includes("commentId", "blogId", "shopId", "content")))
                        .query(query -> query.knn(knn -> knn
                                .field("embedding")
                                .queryVector(queryVector)
                                .k(TOP_K)
                                .numCandidates(20)
                                .filter(filter -> filter.terms(terms -> terms
                                        .field("shopId")
                                        .terms(values -> values.value(shopFilter)))))),
                ReviewHit.class
        );

        if (response.timedOut()) {
            throw new IllegalStateException("评论检索超时");
        }

        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();
    }


}
