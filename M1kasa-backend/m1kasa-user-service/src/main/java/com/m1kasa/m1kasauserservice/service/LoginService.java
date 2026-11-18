package com.m1kasa.m1kasauserservice.service;

import java.time.Duration;

public interface LoginService {

    LoginTokens login(String phone, String code);

    void logout(String token);

    LoginTokens refresh(String refreshToken);

    record LoginTokens(String accessToken, String refreshToken, Duration refreshTtl) {
    }
}
