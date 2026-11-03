package com.m1kasa.user.api;

import java.util.List;

public record FollowPageDTO(
        List<FollowUserDTO> records,
        long total,
        long current,
        long size
) {
}
