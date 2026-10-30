package com.m1kasa.m1kasacontentservice.constant;

public final class BlogLikeRedisKeys {

    private BlogLikeRedisKeys() {
    }

    // 当前用户点赞状态
    public static String state(Long blogId) {
        return "m1kasa:blog:like:state:{" + blogId + "}";
    }

    // 笔记点赞总数
    public static String count(Long blogId) {
        return "m1kasa:blog:like:count:{" + blogId + "}";
    }

    // 待发送的最新状态
    public static String outbox(Long blogId) {
        return "m1kasa:blog:like:outbox:{" + blogId + "}";
    }
}
