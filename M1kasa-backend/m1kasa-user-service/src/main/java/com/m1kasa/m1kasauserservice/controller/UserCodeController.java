package com.m1kasa.m1kasauserservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasauserservice.service.SmsCodeService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/user")
public class UserCodeController {
    private final SmsCodeService smsCodeService;

    public UserCodeController(SmsCodeService smsCodeService) {
        this.smsCodeService = smsCodeService;
    }

    @PostMapping("/code")
    public Result<Void> sendCode(@RequestParam String phone){
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.fail("手机号格式错误");
        }
        return smsCodeService.sendCode(phone)?
                Result.ok():Result.fail("发送太频繁,请60秒后重试");
    }
}
