package com.m1kasa.m1kasasearchservice.embedding;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class SiliconFlowEmbeddingService {
    private static final String MODEL = "BAAI/bge-m3";
    private static final int DIMENSIONS=1024;
    private final RestClient client;
    private final String apikey;

    public SiliconFlowEmbeddingService(
            @Value("${m1kasa.embedding.base-url}") String baseUrl,
            @Value("${m1kasa.embedding.api-key}") String apikey) {
        this.apikey = apikey;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client=RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
    public float[] embed(String text){
        if (text==null||text.isBlank()){
            throw new IllegalArgumentException("向量化文本不能为空");
        }
        if (apikey.isBlank()){
            throw new IllegalArgumentException("请配置 SILICONFLOW_API_KEY");
        }
        EmbeddingResponse response = client.post()
                .uri("/v1/embeddings")
                .headers(httpHeaders -> httpHeaders.setBearerAuth(apikey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", MODEL, "input", text))
                .retrieve()
                .body(EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().size() != 1) {
            throw new IllegalStateException("硅基流动未返回预期的向量结果");
        }

        float[] vector = response.data().get(0).embedding();
        if (vector == null || vector.length != DIMENSIONS) {
            throw new IllegalStateException("BGE-M3 向量维度应为 1024");
        }

        return vector;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<EmbeddingItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingItem(float[] embedding) {
    }
}
