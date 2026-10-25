package com.m1kasa.m1kasauserservice.web;

public record LoginRequest(
        String phone,
        String code
) {
}
