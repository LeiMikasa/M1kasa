package com.m1kasa.m1kasagateway.config;

import com.m1kasa.common.base.auth.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenService jwtTokenService(
        @Value("${M1KASA_JWT_SECRET_BASE64}")
        String secret
    ){
        return new JwtTokenService(secret);
    }
}
