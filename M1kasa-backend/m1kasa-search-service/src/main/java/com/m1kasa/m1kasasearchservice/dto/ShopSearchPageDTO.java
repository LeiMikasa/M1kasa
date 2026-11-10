package com.m1kasa.m1kasasearchservice.dto;

import com.m1kasa.m1kasasearchservice.document.ShopSearchDocument;

import java.util.List;

public record ShopSearchPageDTO(
        List<ShopSearchDocument> records,
        long total,
        int current,
        int size
) {
}
