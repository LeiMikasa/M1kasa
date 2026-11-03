package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.auth.annotation.AllowAnonymous;
import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.*;
import com.m1kasa.m1kasacontentservice.service.BlogLikeService;
import com.m1kasa.m1kasacontentservice.service.BlogPublishService;
import com.m1kasa.m1kasacontentservice.service.BlogQueryService;
import feign.FeignException;
import org.springframework.transaction.TransactionException;
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
    public BlogController(BlogLikeService blogLikeService, BlogQueryService blogQueryService, BlogPublishService blogPublishService) {
        this.blogLikeService = blogLikeService;
        this.blogQueryService = blogQueryService;
        this.blogPublishService = blogPublishService;
    }
    private final BlogLikeService blogLikeService;
    private final BlogQueryService blogQueryService;
    private final BlogPublishService blogPublishService;
    private static final Logger log =
            LoggerFactory.getLogger(BlogController.class);
    @AllowAnonymous
    @GetMapping("/{id}")
    public ResponseEntity<Result<BlogDetailDTO>> findById(
            @PathVariable("id") Long id
    ) {

        if (id <= 0) {
            return ResponseEntity.badRequest()
                    .body(Result.fail("笔记 ID 必须大于 0"));
        }

        try {
            BlogDetailDTO detail = blogQueryService.findById(id);

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

        } catch (IllegalStateException | DataAccessException ex) {
            log.error("查询笔记详情失败，blogId={}", id, ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "笔记详情暂不可用，请稍后重试"
                    ));
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

        } catch (DataAccessException | TransactionException ex) {
            log.error("发布笔记数据库事务失败", ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "发布结果暂未确认，请先查看我的笔记"
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
    @GetMapping("/{id}/like")
    public ResponseEntity<Result<BlogLikeDTO>> getLikeState(
            @PathVariable("id") Long id
    ) {
        try {
            BlogLikeDTO result = blogLikeService.getLikeState(id);
            return ResponseEntity.ok(Result.ok(result));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException
                 | DataAccessException
                 | TransactionException ex) {
            log.error("查询点赞状态失败，blogId={}", id, ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "点赞状态暂不可用，请稍后重试"
                    ));
        }
    }

    @PutMapping(
            value = "/{id}/like",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Result<BlogLikeDTO>> setLiked(
            @PathVariable("id") Long id,
            @RequestBody BlogLikeRequest request
            ){
        //必须明确传入true或false
        if (request==null|| request.liked()==null){
            return ResponseEntity.badRequest()
                    .body(Result.fail("请明确指定liked为true或false"));
        }
        try {
            BlogLikeDTO result = blogLikeService.setLiked(
                    id, request.liked()
            );
            return ResponseEntity.ok(Result.ok(result));
        }catch (IllegalArgumentException ex){
            return ResponseEntity.badRequest().body(Result.fail(ex.getMessage()));
        }catch (IllegalStateException
                | DataAccessException
                | TransactionException ex) {

            log.error("处理笔记点赞失败，blogId={}", id, ex);

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "点赞暂不可用，结果请以查询到的状态为准"
                    ));
        }

    }
    @AllowAnonymous
    @GetMapping("/feed")
    public ResponseEntity<Result<BlogFeedPageDTO>> findFeed(
            @RequestParam(
                    name = "current",
                    defaultValue = "1"
            ) int current,
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            ) int size
    ){
        try {
            BlogFeedPageDTO page = blogQueryService.findFeed(
                    current, size
            );
            return ResponseEntity.ok(Result.ok(page));
        }catch (IllegalArgumentException ex){
            //分页参数不合法
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));
        }catch (FeignException ex){
            //调用服务时，登录态可能已经失效
            if (ex.status()==401){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Result.fail("登录已过期，请重新登录"));
            }
            log.error("查询笔记作者资料失败",ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.fail("作者资料暂不可用，请稍后重试"));
        } catch (IllegalStateException | DataAccessException ex) {
            // 用户服务返回异常结果、图片存储或数据库异常等
            log.error("查询笔记广场失败", ex);

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail("笔记广场暂不可用，请稍后重试"));
        }
    }
    @AllowAnonymous
    @GetMapping("/of/user/{userId}")
    public ResponseEntity<Result<BlogPageDTO>> findUserBlogs(
            @PathVariable("userId") Long userId,
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
            BlogPageDTO page = blogQueryService.findUserBlogs(
                    userId,
                    current,
                    size
            );

            return ResponseEntity.ok(Result.ok(page));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException | DataAccessException ex) {
            log.error(
                    "查询用户笔记失败，userId={}, current={}, size={}",
                    userId,
                    current,
                    size,
                    ex
            );

            return ResponseEntity.status(503)
                    .body(Result.fail("用户笔记暂不可用，请稍后重试"));
        }
    }

}
