package com.m1kasa.m1kasasearchservice.service;

import com.m1kasa.m1kasasearchservice.dto.ShopSearchPageDTO;

import java.io.IOException;

public interface ShopSearchService {
    ShopSearchPageDTO search(
            String keyword,
            Long typeId,
            int current,
            int size
    ) throws IOException;
}
