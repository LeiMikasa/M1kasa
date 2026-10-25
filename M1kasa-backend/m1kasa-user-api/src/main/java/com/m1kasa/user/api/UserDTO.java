package com.m1kasa.user.api;
//对外展示的用户信息
public record UserDTO(
        Long id,
        String nickName,
        String icon
) {
}
