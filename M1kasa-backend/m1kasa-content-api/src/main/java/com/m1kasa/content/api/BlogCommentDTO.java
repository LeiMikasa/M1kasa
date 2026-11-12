package com.m1kasa.content.api;

import java.time.LocalDateTime;

public record BlogCommentDTO(
        String id,
        String userId,
        String authorName,
        String authorIcon,
        String content,
        LocalDateTime createTime,
        String replyToCommentId,
        String replyToAuthorName,
        int replyCount,
        boolean deleted
) {
}
