package com.m1kasa.m1kasacontentservice.controller;

import com.m1kasa.common.auth.annotation.AllowAnonymous;
import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.BlogCommentCreateRequest;
import com.m1kasa.content.api.BlogCommentPageDTO;
import com.m1kasa.m1kasacontentservice.service.BlogCommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/blog/{blogId}/comments")
public class BlogCommentController {

    private final BlogCommentService commentService;

    public BlogCommentController(BlogCommentService commentService) {
        this.commentService = commentService;
    }
    @AllowAnonymous
    @GetMapping("/{rootId}/replies")
    public ResponseEntity<Result<BlogCommentPageDTO>> listReplies(
            @PathVariable("blogId") Long blogId,
            @PathVariable("rootId") Long rootId,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        try {
            return ResponseEntity.ok(
                    Result.ok(commentService.listReplies(blogId, rootId, beforeId, size))
            );
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Result.fail(ex.getReason()));
        }
    }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Result<String>> publish(
            @PathVariable("blogId") Long blogId,
            @RequestBody BlogCommentCreateRequest request
    ) {
        try {
            Long commentId = commentService.publish(blogId, request.content(),request.replyToCommentId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Result.ok(commentId.toString()));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Result.fail(ex.getReason()));
        }
    }
    @AllowAnonymous
    @GetMapping
    public ResponseEntity<Result<BlogCommentPageDTO>> list(
        @PathVariable("blogId") Long blogId,
        @RequestParam(name = "beforeId",required = false) Long beforeId,
        @RequestParam(name = "size",defaultValue = "10") int size
    ){
        try {
            return ResponseEntity.ok(
                    Result.ok(commentService.listByBlog(blogId,beforeId,size))
            );
        }catch (ResponseStatusException ex){
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Result.fail(ex.getReason()));
        }
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Result<Void>> delete(
            @PathVariable("blogId") Long blogId,
            @PathVariable("commentId") Long commentId
    ) {
        try {
            commentService.delete(blogId, commentId);
            return ResponseEntity.ok(Result.ok());
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Result.fail(ex.getReason()));
        }
    }
}
