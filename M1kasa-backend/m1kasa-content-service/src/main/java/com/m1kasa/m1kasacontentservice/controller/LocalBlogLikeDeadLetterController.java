package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.BlogLikeDeadLetterDetailDTO;
import com.m1kasa.content.api.BlogLikeDeadLetterPageDTO;
import com.m1kasa.m1kasacontentservice.service.BlogLikeDeadLetterService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 点赞死信的本机调试查询接口。
 */
@RestController
@Profile("local")
@RequestMapping("/local/mq/blog-like-dead-letters")
public class LocalBlogLikeDeadLetterController {

    private static final Logger log = LoggerFactory.getLogger(
            LocalBlogLikeDeadLetterController.class
    );

    private static final Set<String> LOOPBACK_ADDRESSES = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    );

    private final BlogLikeDeadLetterService deadLetterService;

    public LocalBlogLikeDeadLetterController(
            BlogLikeDeadLetterService deadLetterService
    ) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping
    public ResponseEntity<Result<BlogLikeDeadLetterPageDTO>> findPage(
            @RequestParam(name = "status", required = false)
            Integer status,
            @RequestParam(name = "current", defaultValue = "1")
            int current,
            @RequestParam(name = "size", defaultValue = "10")
            int size,
            HttpServletRequest request
    ) {
        if (!isLoopback(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail("仅允许本机直接访问该调试接口"));
        }

        try {
            BlogLikeDeadLetterPageDTO page =
                    deadLetterService.findPage(status, current, size);
            return ResponseEntity.ok(Result.ok(page));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (DataAccessException ex) {
            log.error(
                    "查询点赞死信列表失败，status={}, current={}, size={}",
                    status, current, size, ex
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail("死信查询暂不可用，请稍后重试"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<BlogLikeDeadLetterDetailDTO>> findById(
            @PathVariable("id") Long id,
            HttpServletRequest request
    ) {
        if (!isLoopback(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail("仅允许本机直接访问该调试接口"));
        }

        try {
            BlogLikeDeadLetterDetailDTO detail =
                    deadLetterService.findById(id);

            if (detail == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("死信记录不存在"));
            }

            return ResponseEntity.ok(Result.ok(detail));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (DataAccessException ex) {
            log.error("查询点赞死信详情失败，id={}", id, ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail("死信查询暂不可用，请稍后重试"));

        } catch (IllegalStateException ex) {
            log.error("点赞死信记录数据异常，id={}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.fail("死信记录数据异常，请查看服务端日志"));
        }
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            HttpServletRequest request
    ) {
        if (!isLoopback(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail("仅允许本机直接访问该调试接口"));
        }

        return ResponseEntity.badRequest()
                .body(Result.fail("请求参数格式不正确，请使用整数"));
    }

    private static boolean isLoopback(HttpServletRequest request) {
        return LOOPBACK_ADDRESSES.contains(request.getRemoteAddr());
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Result<String>> replay(
            @PathVariable("id") Long id,
            HttpServletRequest request
    ) {
        if (!isLoopback(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.fail(
                            "仅允许本机直接访问该调试接口"
                    ));
        }

        try {
            String message = deadLetterService.replay(id);

            return ResponseEntity.ok(
                    Result.ok(message)
            );

        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));

        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Result.fail(ex.getMessage()));

        } catch (AmqpException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Result.fail(ex.getMessage()));

        } catch (DataAccessException ex) {
            log.error(
                    "处理点赞死信重发请求时数据库异常，id={}",
                    id,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail(
                            "死信重发暂不可用，请稍后重试"
                    ));
        }
    }
}
