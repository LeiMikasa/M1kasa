package com.m1kasa.order.api;

import java.time.LocalDateTime;

public record DeadLetterSummaryDTO(
        String id,
        String messageId,
        String sourceQueue,
        String deathReason,
        Integer status,
        Integer replayCount,
        String lastError,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
