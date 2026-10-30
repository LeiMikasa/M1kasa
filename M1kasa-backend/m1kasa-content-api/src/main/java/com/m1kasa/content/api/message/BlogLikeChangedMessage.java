package com.m1kasa.content.api.message;

public record BlogLikeChangedMessage(
        //笔记ID
        Long blogId,
        //点赞用户ID
        Long userId,
        //目标状态 true已点赞，false未点赞
        Boolean liked,
        //当前用户对当前笔记的状态版本
        Long version,
        //操作发生时间，毫秒时间戳
        Long occurredAt
) {
}
