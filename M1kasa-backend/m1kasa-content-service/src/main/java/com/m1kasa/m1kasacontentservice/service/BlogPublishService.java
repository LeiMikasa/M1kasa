package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogPublishRequest;

public interface BlogPublishService {
    /*
    * 发布笔记，返回生成的笔记ID
    * */
    Long publish(BlogPublishRequest request);
}
