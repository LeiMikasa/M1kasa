package com.m1kasa.content.api.message;

public record BlogCommentSearchMessage(
        Long commentId,
        Long blogId,
        Long shopId,
        String content
) {
    public static final String EXCHANGE = "m1kasa.content.comment.search.exchange";
    public static final String ROUTING_KEY = "blog.comment.search.created";
    public static final String QUEUE = "m1kasa.blog.comment.search.queue";
}
