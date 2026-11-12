package com.m1kasa.content.api;

public record BlogCommentCreateRequest(
        String content,
        Long replyToCommentId
        ) {
}
