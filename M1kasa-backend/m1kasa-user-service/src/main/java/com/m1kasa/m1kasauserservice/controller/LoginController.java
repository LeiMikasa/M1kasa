package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.LoginService;
import com.m1kasa.m1kasauserservice.web.LoginRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest request) {
        if (request.phone() == null
                || !request.phone().matches("^1[3-9]\\d{9}$")
                || request.code() == null
                || !request.code().matches("\\d{6}")) {
            return Result.fail("手机号或验证码格式错误");
        }

        String token = loginService.login(request.phone(), request.code());
        return token == null
                ? Result.fail("验证码错误或已过期")
                : Result.ok(token);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ){
        if (!authorization.startsWith("Bearer ")){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token=authorization.substring("Bearer ".length()).trim();

        if (!StringUtils.hasText(token)){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        loginService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
