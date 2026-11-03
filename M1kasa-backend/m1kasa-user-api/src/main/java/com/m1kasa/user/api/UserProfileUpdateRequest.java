package com.m1kasa.user.api;

// 前端提交的个人资料修改请求
public record UserProfileUpdateRequest(
        String nickName,
        String avatarObjectKey
) {
}
