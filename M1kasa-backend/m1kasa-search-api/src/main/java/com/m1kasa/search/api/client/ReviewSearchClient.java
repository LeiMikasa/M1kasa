package com.m1kasa.search.api.client;

import com.m1kasa.common.dto.Result;
import com.m1kasa.search.api.dto.ReviewHit;
import com.m1kasa.search.api.dto.ReviewSearchRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "m1kasa-search-service")
public interface ReviewSearchClient {

    @PostMapping("/internal/search/reviews/query")
    Result<List<ReviewHit>> search(@RequestBody ReviewSearchRequest request);
}
