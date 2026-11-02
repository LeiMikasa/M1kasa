package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogDetailDTO;
import com.m1kasa.content.api.BlogFeedPageDTO;
import com.m1kasa.content.api.BlogPageDTO;

public interface BlogQueryService {
    BlogDetailDTO findById(Long id);
    BlogPageDTO findMyBlogs(int current,int size);
    BlogFeedPageDTO findFeed(
      int current,
      int size
    );
}
