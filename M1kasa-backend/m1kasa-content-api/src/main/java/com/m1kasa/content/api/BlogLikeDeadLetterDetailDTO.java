package com.m1kasa.content.api;

import java.time.LocalDateTime;

public record BlogLikeDeadLetterDetailDTO(
        String id,
        String messageKey,
        String messageId,
        String sourceQueue,
        String contentType,
        String contentEncoding,
        String bodyBase64,
        String bodyText,
        String headersJson,
        String deathReason,
        Integer status,
        Long captureVersion,
        Integer replayCount,
        String lastError,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
