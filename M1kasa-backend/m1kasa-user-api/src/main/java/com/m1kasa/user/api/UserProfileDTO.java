package com.m1kasa.user.api;

public record UserProfileDTO(
        String userId,
        String nickName,
        String icon,
        long followingCount,
        long followersCount
) {
}
