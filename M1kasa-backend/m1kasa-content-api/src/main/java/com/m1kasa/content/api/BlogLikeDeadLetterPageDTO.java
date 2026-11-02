package com.m1kasa.content.api;

import java.util.List;

public record BlogLikeDeadLetterPageDTO(
        List<BlogLikeDeadLetterSummaryDTO> records,
        long total,
        int current,
        int size
) {
}
