package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogLikeDTO;

public interface BlogLikeService {

    BlogLikeDTO setLiked(Long blogId,boolean liked);

    BlogLikeDTO getLikeState(Long blogId);
}
