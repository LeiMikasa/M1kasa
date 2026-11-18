package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.auth.annotation.AllowAnonymous;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.LoginService;
import com.m1kasa.m1kasauserservice.service.RefreshTokenService;
import com.m1kasa.m1kasauserservice.web.LoginRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/user")
public class LoginController {
    private final LoginService loginService;
    @Value("${M1KASA_REFRESH_COOKIE_SECURE:true}")
    private boolean refreshCookieSecure;

    private final RefreshTokenService refreshTokenService;
    public LoginController(LoginService loginService, RefreshTokenService refreshTokenService) {
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest request
    , HttpServletResponse response) {
        if (request.phone() == null
                || !request.phone().matches("^1[3-9]\\d{9}$")
                || request.code() == null
                || !request.code().matches("\\d{6}")) {
            return Result.fail("手机号或验证码格式错误");
        }
        LoginService.LoginTokens tokens = loginService.login(request.phone(), request.code());
        if (tokens == null) {
            return Result.fail("验证码错误或已过期");
        }

        writeRefreshCookie(response, tokens.refreshToken(),tokens.refreshTtl());
        return Result.ok(tokens.accessToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletResponse response
    ){
        if (!authorization.startsWith("Bearer ")){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token=authorization.substring("Bearer ".length()).trim();

        if (!StringUtils.hasText(token)){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        loginService.logout(token);
        writeRefreshCookie(response, "", Duration.ZERO);
        return ResponseEntity.noContent().build();
    }
    private static final String REFRESH_COOKIE = "m1kasa_refresh";

    @AllowAnonymous
    @PostMapping("refresh")
    public ResponseEntity<Result<String>> refresh(
        @CookieValue(name = REFRESH_COOKIE,required = false)String refreshToken,
        HttpServletResponse response
    ){
        LoginService.LoginTokens tokens = loginService.refresh(refreshToken);
        if (tokens==null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    Result.fail("刷新凭证无效或已过期，请重新登录")
            );
        }
        writeRefreshCookie(response,tokens.refreshToken(),tokens.refreshTtl());
        return ResponseEntity.ok(Result.ok(tokens.accessToken()));

    }
    private void writeRefreshCookie(
            HttpServletResponse response,
            String token,
            Duration ttl
    ) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/user")
                .maxAge(ttl)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
