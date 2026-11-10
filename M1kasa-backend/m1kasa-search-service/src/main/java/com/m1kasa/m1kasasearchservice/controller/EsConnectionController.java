package com.m1kasa.m1kasasearchservice.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import com.m1kasa.common.dto.Result;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@Profile("local")
@RestController
@RequestMapping("/internal/search")
public class EsConnectionController {
    private final ElasticsearchClient elasticsearchClient;

    public EsConnectionController(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }
    @GetMapping("es-info")
    public Result<Map<String,String>> info() throws IOException {
        InfoResponse response = elasticsearchClient.info();
        //取出我们关心的两个字段
        Map<String, String> data = Map.of(
                "clusterName", response.clusterName(),
                "version", response.version().number()
        );
        return Result.ok(data);
    }
}
