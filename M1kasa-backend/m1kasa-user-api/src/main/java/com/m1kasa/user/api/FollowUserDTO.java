package com.m1kasa.user.api;

import java.time.LocalDateTime;

public record FollowUserDTO(
        String userId,
        String nickName,
        String icon,
        LocalDateTime followTime,
        boolean followed
) {
}
