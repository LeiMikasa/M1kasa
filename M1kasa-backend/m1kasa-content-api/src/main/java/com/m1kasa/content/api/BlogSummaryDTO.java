package com.m1kasa.content.api;

import java.time.LocalDateTime;

public record BlogSummaryDTO(
        String id,
        String title,
        String coverImage,
        Integer liked,
        Integer comments,
        LocalDateTime createTime
) {
}
