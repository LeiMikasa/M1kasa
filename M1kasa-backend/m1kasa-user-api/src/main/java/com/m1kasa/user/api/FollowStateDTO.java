package com.m1kasa.user.api;

public record FollowStateDTO(
        String targetUserId,
        boolean followed
) {
}
