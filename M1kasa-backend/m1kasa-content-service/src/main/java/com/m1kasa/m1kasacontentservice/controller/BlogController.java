package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.BlogDetailDTO;
import com.m1kasa.content.api.BlogPageDTO;
import com.m1kasa.content.api.BlogPublishRequest;
import com.m1kasa.m1kasacontentservice.service.BlogPublishService;
import com.m1kasa.m1kasacontentservice.service.BlogQueryService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog")
public class BlogController {
    public BlogController(BlogQueryService blogQueryService, BlogPublishService blogPublishService) {
        this.blogQueryService = blogQueryService;
        this.blogPublishService = blogPublishService;
    }

    private final BlogQueryService blogQueryService;
    private final BlogPublishService blogPublishService;
    private static final Logger log =
            LoggerFactory.getLogger(BlogController.class);
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
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Result<String>> publish(
            @RequestBody BlogPublishRequest request
    ) {
        try {
            Long blogId = blogPublishService.publish(request);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Result.ok(blogId.toString()));

        } catch (IllegalArgumentException ex) {
            // 标题、正文、图片路径等参数不合法
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException ex) {
            // 存储服务异常、用户上下文缺失等
            log.error("发布笔记失败", ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "发布暂不可用，请稍后重试"
                    ));

        } catch (DataAccessException ex) {
            // 数据库异常只记录到服务端日志
            log.error("保存笔记失败", ex);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail(
                            "笔记保存失败，请稍后重试"
                    ));
        }
    }
    @GetMapping("/of/me")
    public ResponseEntity<Result<BlogPageDTO>> findMyBlogs(
            @RequestParam(
                    name = "current",
                    defaultValue = "1"
            ) int current,
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            ) int size
    ) {
        try {
            BlogPageDTO page = blogQueryService.findMyBlogs(
                    current,
                    size
            );

            return ResponseEntity.ok(Result.ok(page));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException ex) {
            log.error("查询我的笔记失败", ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "笔记查询暂不可用，请稍后重试"
                    ));

        } catch (DataAccessException ex) {
            log.error("查询笔记数据库失败", ex);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail(
                            "查询笔记失败，请稍后重试"
                    ));
        }
    }
}
