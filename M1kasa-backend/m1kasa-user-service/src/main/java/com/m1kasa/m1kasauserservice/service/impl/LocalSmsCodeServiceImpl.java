package com.m1kasa.m1kasauserservice.service.impl;

import com.m1kasa.m1kasauserservice.service.SmsCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
@Profile("local")
public class LocalSmsCodeServiceImpl implements SmsCodeService {
    private static final String CODE_PREFIX="m1kasa:login:code:";
    private static final String RATE_PREFIX="m1kasa:login:code:rate:";
    private final StringRedisTemplate stringRedisTemplate;
    private static final Logger log =
            LoggerFactory.getLogger(LocalSmsCodeServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    public LocalSmsCodeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean sendCode(String phone) {
         Boolean allowed=stringRedisTemplate.opsForValue()
                 .setIfAbsent(
                         RATE_PREFIX+phone,"1", Duration.ofSeconds(60)
                 );
         if (!Boolean.TRUE.equals(allowed)){
             return false;
         }
        String code = String.format(
                Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)
        );
         stringRedisTemplate.opsForValue().set(
                 CODE_PREFIX+phone,code,Duration.ofMinutes(5)
         );
         log.info("本地验证码：手机号尾号{},验证码{}",phone.substring(7),code);
         return true;

    }
}
