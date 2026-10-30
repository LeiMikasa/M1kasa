package com.m1kasa.content.api;

public record BlogPublishRequest(
        String title,
        String coverImage,
        String content
) {
}
