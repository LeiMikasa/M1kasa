package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.message.BlogLikeChangedMessage;

public interface BlogLikeSyncService {
    //将一条点赞变更同步到数据
    void sync(BlogLikeChangedMessage message);
}
