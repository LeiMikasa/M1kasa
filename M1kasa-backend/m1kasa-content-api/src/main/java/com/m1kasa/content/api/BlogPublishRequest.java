package com.m1kasa.content.api;

import java.util.List;

public record BlogPublishRequest(
        String title,
        String coverImage,
        String content,
        List<String> imageObjectKeys,
        Long shopId
) {
}
