package com.m1kasa.order.api;

public record DeadLetterDetailDTO(
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
        Integer replayCount,
        String lastError
) {
}
