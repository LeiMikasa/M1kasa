package com.m1kasa.content.api;


public record BlogLikeDTO(
    boolean likedByMe, //当前用户是否已点赞，
    int liked  //笔记点赞总数
) {

}
