package com.m1kasa.content.api;

import java.util.List;

public record BlogCommentPageDTO(
        List<BlogCommentDTO> records,
        String nextCursor,
        boolean hasMore
) {
}
