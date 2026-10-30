package com.m1kasa.content.api;

import java.util.List;

public record BlogPageDTO(
        List<BlogSummaryDTO> records,
        long total,
        long current,
        long size
) {
}
