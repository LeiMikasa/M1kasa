package com.m1kasa.m1kasasearchservice.rerank;

import co.elastic.clients.elasticsearch.inference.RerankResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SiliconFlowRerankService {

    private static final String MODEL="BAAI/bge-reranker-v2-m3";
    private final RestClient client;
    private final String apiKey;

    public SiliconFlowRerankService(
            @Value("${m1kasa.embedding.base-url}") String baseUrl,
            @Value("${m1kasa.embedding.api-key}") String apiKey) {
        this.apiKey = apiKey;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory=
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client=RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
    public List<RerankItem> rerank(
        String question,
        List<String> documents,
        int topN
    ){
        if (question==null||question.isBlank()||topN<=0){
            throw new IllegalArgumentException("请提供有效问题和返回条数");
        }
        if (documents.isEmpty()){
            return List.of();
        }
        int limit=Math.min(topN,documents.size());
        RerankResponse response = client.post()
                .uri("/v1/rerank")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", MODEL,
                        "query", question,
                        "documents", documents,
                        "top_n", limit
                ))
                .retrieve()
                .body(RerankResponse.class);
        if (response==null||response.results()==null||response.results().size()!=limit) {
            throw new IllegalStateException("硅基流动未返回预期的重排结果");
        }
        return response.results();

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResponse(List<RerankItem> results){

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankItem(
            Integer index,
            @JsonProperty("relevance_score") Double relevanceScore
    ){

    }
}
