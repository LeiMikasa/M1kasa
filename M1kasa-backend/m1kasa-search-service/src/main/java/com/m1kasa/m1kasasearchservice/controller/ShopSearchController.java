package com.m1kasa.m1kasasearchservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasasearchservice.dto.ShopSearchPageDTO;
import com.m1kasa.m1kasasearchservice.service.ShopSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/search/shops")
public class ShopSearchController {
    private final ShopSearchService shopSearchService;

    public ShopSearchController(ShopSearchService shopSearchService) {
        this.shopSearchService = shopSearchService;
    }
    @GetMapping
    public Result<ShopSearchPageDTO> search(
            @RequestParam(name = "keyword",required = false) String keyword,
            @RequestParam(name = "typeId",required = false) Long typeId,
            @RequestParam(name = "current",defaultValue = "1") int current,
            @RequestParam(name = "size",defaultValue = "10") int size
    ) throws IOException {
        // 校验分页参数，避免无效请求和过大的单页数据。
        if (current < 1 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "页码必须大于 0，每页数量必须在 1 到 50 之间"
            );
        }

        if (typeId != null && typeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类 ID 必须大于 0");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索关键词不能超过 100 个字符");
        }

        // 使用 long 计算偏移，并限制在当前 ES 默认分页窗口内。
        long from = (current - 1L) * size;
        if (from + size > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前搜索最多支持查看前 10000 条结果");
        }

        // 调用已经写好的搜索 Service，并使用项目统一响应格式。
        return Result.ok(shopSearchService.search(keyword, typeId, current, size));
    }
}
