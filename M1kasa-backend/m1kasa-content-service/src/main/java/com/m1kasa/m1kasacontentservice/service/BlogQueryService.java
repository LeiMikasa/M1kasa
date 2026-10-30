package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogDetailDTO;
import com.m1kasa.content.api.BlogPageDTO;

public interface BlogQueryService {
    BlogDetailDTO findById(Long id,String authorization);
    BlogPageDTO findMyBlogs(int current,int size);
}
