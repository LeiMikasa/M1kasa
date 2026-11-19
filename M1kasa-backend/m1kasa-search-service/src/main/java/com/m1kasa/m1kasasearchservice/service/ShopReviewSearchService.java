package com.m1kasa.m1kasasearchservice.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.m1kasa.m1kasasearchservice.rerank.SiliconFlowRerankService;
import com.m1kasa.search.api.dto.ReviewHit;
import com.m1kasa.m1kasasearchservice.embedding.SiliconFlowEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.*;

@Service
public class ShopReviewSearchService {

    private static final String INDEX = "m1kasa_shop_review_v2";
    private static final int TOP_K = 3;

    private final ElasticsearchClient elasticsearchClient;
    private final SiliconFlowEmbeddingService embeddingService;
    private final double keywordWeight;
    private static final Logger log =
            LoggerFactory.getLogger(ShopReviewSearchService.class);

    private final SiliconFlowRerankService rerankService;
    private final boolean rerankEnabled;
    public ShopReviewSearchService(
            ElasticsearchClient elasticsearchClient,
            SiliconFlowEmbeddingService embeddingService,
            @Value("${m1kasa.search.review.keyword-weight:0.02}") double keywordWeight,
            SiliconFlowRerankService rerankService,
            @Value("${m1kasa.search.review.rerank-enabled:true}") boolean rerankEnabled
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.rerankService = rerankService;
        if (!(keywordWeight >= 0.0 && keywordWeight <= 1.0)) {
            throw new IllegalArgumentException("评论关键词权重必须在 0 到 1 之间");
        }
        this.embeddingService = embeddingService;
        this.keywordWeight = keywordWeight;
        this.rerankEnabled=rerankEnabled;
    }
    /*
    * 统一入口
    * */

    private static final int CANDIDATE_LIMIT=10;
    private static final int RRF_RANK_CONSTANT=60;
    public List<ReviewHit> search(String question,List<Long> shopIds,String keyword)
    throws IOException{
        List<FieldValue> shopFilter = shopIds.stream().distinct().map(id -> FieldValue.of(id.toString())).toList();
        int candidateLimit=rerankEnabled?CANDIDATE_LIMIT:TOP_K;
        List<ReviewHit> candidates;
        if (keywordWeight==0.0||keyword==null||keyword.isBlank()){
            candidates=searchByVector(question,shopFilter,CANDIDATE_LIMIT);
        }else {
            List<ReviewHit> keywordHits=searchByKeyWord(keyword.strip(),shopFilter);
            List<ReviewHit> vectorHits =
                    searchByVector(question, shopFilter, CANDIDATE_LIMIT);
            candidates = fuseByRank(keywordHits, vectorHits, keywordWeight, candidateLimit);
        }
        if (!rerankEnabled || candidates.size() <= 1) {
            return candidates;
        }
        return rerankCandidates(question, candidates);

        //        if (keywordWeight == 0.0 || keyword == null || keyword.isBlank()) {
//            return searchByVector(question, shopFilter, TOP_K);
//        }

//        List<ReviewHit> keywordHits = searchByKeyWord(keyword.strip(), shopFilter);
//        List<ReviewHit> vectorHits = searchByVector(question, shopFilter, CANDIDATE_LIMIT);
//
//        return fuseByRank(keywordHits, vectorHits, keywordWeight);
    }

    /*
    * 用重排返回的下标拿回原评论
    * */
    private List<ReviewHit> rerankCandidates(
            String question,List<ReviewHit> candidates
    ){
        List<ReviewHit> fallback=candidates.stream()
                .limit(TOP_K)
                .toList();
        List<String> documents = candidates.stream().map(ReviewHit::content).toList();
        try {
            List<SiliconFlowRerankService.RerankItem> results = rerankService.rerank(question, documents, TOP_K);
            List<ReviewHit> ranked=new ArrayList<>();
            Set<Integer> seenIndices=new HashSet<>();
            for (SiliconFlowRerankService.RerankItem item : results) {
                Integer index=item==null?null:item.index();
                if (index==null||index<0||index>=candidates.size()
                ||!seenIndices.add(index)){
                    throw new IllegalStateException("重排结果包含无效或重复的评论下标");
                }
                ranked.add(candidates.get(index));
            }
            return ranked;
        } catch (RestClientException | IllegalStateException ex) {
            log.warn("评论重排失败，使用召回结果。异常类型={}",
                    ex.getClass().getSimpleName());
            return fallback;
        }

    }
    private List<ReviewHit> fuseByRank(
            List<ReviewHit> keywordHits,
            List<ReviewHit> vectorHits,
            double keywordWeight,
            int limit
    ) {
        Map<String, ReviewHit> reviewsById = new LinkedHashMap<>();
        Map<String, Double> scoresById = new HashMap<>();
        addRankScores(keywordHits, keywordWeight, reviewsById, scoresById);
        addRankScores(vectorHits, 1.0, reviewsById, scoresById);

        return reviewsById.values().stream()
                .sorted(Comparator.comparingDouble(
                        (ReviewHit review) -> scoresById.get(review.commentId())
                ).reversed())
                .limit(limit)
                .toList();
    }

    private void addRankScores(
            List<ReviewHit> hits,
            double weight,
            Map<String, ReviewHit> reviewsById,
            Map<String, Double> scoresById
    ) {
        for (int index = 0; index < hits.size(); index++) {
            ReviewHit review = hits.get(index);
            String commentId = review.commentId();
            if (commentId == null || commentId.isBlank()) {
                continue;
            }
            int rank = index + 1;
            double score = weight / (RRF_RANK_CONSTANT + rank);
            reviewsById.putIfAbsent(commentId, review);
            scoresById.merge(commentId, score, Double::sum);
        }
    }

    private List<ReviewHit> searchByVector(
            String question,
            List<FieldValue> shopFilter,
            int limit
    ) throws IOException {
        float[] embedding = embeddingService.embed(question);
        List<Float> queryVector = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            queryVector.add(value);
        }

        var response = elasticsearchClient.search(search -> search
                        .index(INDEX)
                        .size(limit)
                        .allowPartialSearchResults(false)
                        .source(source -> source.filter(fields ->
                                fields.includes("commentId", "blogId", "shopId", "content")))
                        .query(query -> query.knn(knn -> knn
                                .field("embedding")
                                .queryVector(queryVector)
                                .k(limit)
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

    /*
    * 关键词搜索
    * */
    private List<ReviewHit> searchByKeyWord(
            String keyword,
            List<FieldValue> shopFilter
    ) throws IOException {
        Query keywordQuery = Query.of(query -> query.bool(bool -> bool
                .filter(filter -> filter.terms(terms -> terms
                        .field("shopId")
                        .terms(values -> values.value(shopFilter))))
                .must(must -> must.matchPhrase(phrase -> phrase
                        .field("content")
                        .query(keyword))))
                );

        var response=elasticsearchClient.search
                (request->request
                        .index(INDEX)
                        .size(CANDIDATE_LIMIT)
                        .allowPartialSearchResults(false)
                        .source(source -> source.filter(fields ->
                                fields.includes("commentId", "blogId", "shopId", "content")))
                        .query(keywordQuery),ReviewHit.class);

        if (response.timedOut()){
            throw new IllegalStateException("评论关键词检索超时");
        }
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();


    }


}
