package com.m1kasa.content.api;

import java.time.LocalDateTime;

public record BlogLikeDeadLetterSummaryDTO(
        String id,
        String messageId,
        String sourceQueue,
        String deathReason,
        Integer status,
        Long captureVersion,
        Integer replayCount,
        String lastError,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
