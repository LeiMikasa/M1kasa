package com.m1kasa.content.api;

import java.time.LocalDateTime;

public record BlogFeedItemDTO(
        String id,
        //作者信息
        String userId,
        String authorName,
        String authorIcon,
        //笔记信息
        String title,
        String coverImage,
        Integer liked,
        Integer comments,
        LocalDateTime createTime
) {
}
