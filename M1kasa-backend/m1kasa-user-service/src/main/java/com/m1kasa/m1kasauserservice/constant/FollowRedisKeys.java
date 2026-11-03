package com.m1kasa.m1kasauserservice.constant;

import java.time.Duration;

public final class FollowRedisKeys {

    private FollowRedisKeys() {
    }

    /**
     * 标记：当前用户的完整关注列表已经加载。
     */
    public static final String INITIALIZED_MEMBER = "__READY__";

    /**
     * 关注列表缓存有效期，先设置为 5 分钟。
     */
    public static final Duration CACHE_TTL =
            Duration.ofMinutes(5);

    /**
     * 当前用户关注的所有作者 ID。
     */
    public static String following(Long userId) {
        return "m1kasa:follow:following:{" + userId + "}";
    }
}
