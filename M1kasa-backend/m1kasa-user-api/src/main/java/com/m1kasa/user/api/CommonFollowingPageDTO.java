package com.m1kasa.user.api;

import java.util.List;

public record CommonFollowingPageDTO(
        List<CommonFollowingUserDTO> records,
        long total,
        long current,
        long size
) {
}
