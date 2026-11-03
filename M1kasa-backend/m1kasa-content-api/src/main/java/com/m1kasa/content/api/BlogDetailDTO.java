package com.m1kasa.content.api;

import java.time.LocalDateTime;
import java.util.List;

public record BlogDetailDTO(
        Long id,
        Long shopId,
        Long userId,
        String authorName,
        String authorIcon,
        String title,
        String coverImage,
        List<String> imageUrls,
        String content,
        Integer liked,
        Integer comments,
        LocalDateTime createTime
) {
}
