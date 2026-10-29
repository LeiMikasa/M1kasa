package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.BlogDetailDTO;
import com.m1kasa.m1kasacontentservice.service.BlogQueryService;
import feign.FeignException;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog")
public class BlogController {
    public BlogController(BlogQueryService blogQueryService) {
        this.blogQueryService = blogQueryService;
    }

    private final BlogQueryService blogQueryService;

    @GetMapping("/{id}")
    public ResponseEntity<Result<BlogDetailDTO>> findById(
            @PathVariable("id") Long id,
            @RequestHeader(
                    value = "Authorization",
                    required = false
            ) String authorization
    ) {
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                    .body(Result.fail("请先登录"));
        }

        if (id <= 0) {
            return ResponseEntity.badRequest()
                    .body(Result.fail("笔记 ID 必须大于 0"));
        }

        try {
            BlogDetailDTO detail = blogQueryService.findById(
                    id,
                    authorization
            );

            if (detail == null) {
                return ResponseEntity.status(404)
                        .body(Result.fail("笔记不存在"));
            }

            return ResponseEntity.ok(Result.ok(detail));

        } catch (FeignException ex) {
            if (ex.status() == 401) {
                return ResponseEntity.status(401)
                        .body(Result.fail("登录已过期，请重新登录"));
            }

            return ResponseEntity.status(502)
                    .body(Result.fail("查询作者资料失败，请稍后重试"));

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(502)
                    .body(Result.fail(ex.getMessage()));
        }
    }
}
