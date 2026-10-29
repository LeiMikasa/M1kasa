package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogDetailDTO;

public interface BlogQueryService {
    BlogDetailDTO findById(Long id,String authorization);
}
