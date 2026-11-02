package com.m1kasa.content.api;

import java.util.List;

public record BlogFeedPageDTO(
        List<BlogFeedItemDTO> records,
        long total,
        long current,
        long size
) {
}
