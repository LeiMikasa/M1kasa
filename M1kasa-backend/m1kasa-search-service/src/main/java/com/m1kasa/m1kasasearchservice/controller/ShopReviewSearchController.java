package com.m1kasa.m1kasasearchservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.search.api.dto.ReviewHit;
import com.m1kasa.search.api.dto.ReviewSearchRequest;
import com.m1kasa.m1kasasearchservice.service.ShopReviewSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
public class ShopReviewSearchController {

    private final ShopReviewSearchService searchService;

    public ShopReviewSearchController(ShopReviewSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/internal/search/reviews/query")
    public Result<List<ReviewHit>> search(@RequestBody ReviewSearchRequest request)
            throws IOException {
        if (request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }

        if (request.shopIds() == null || request.shopIds().isEmpty()
                || request.shopIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "请提供有效的店铺 ID 列表"
            );
        }

        return Result.ok(searchService.search(request.question().strip(), request.shopIds()));
    }


}
