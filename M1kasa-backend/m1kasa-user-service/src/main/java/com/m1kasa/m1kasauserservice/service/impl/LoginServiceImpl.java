package com.m1kasa.m1kasauserservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.common.base.auth.JwtTokenService;
import com.m1kasa.m1kasauserservice.entity.User;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import com.m1kasa.m1kasauserservice.service.LoginService;
import com.m1kasa.m1kasauserservice.service.RefreshTokenService;
import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LoginServiceImpl implements LoginService {
    private static final DefaultRedisScript<Long> VERIFY_CODE=
            new DefaultRedisScript<>(
                    """
                            local stored = redis.call('GET', KEYS[1])
                            if stored == ARGV[1] then
                                return redis.call('DEL', KEYS[1])
                            end
                            return 0
                     
                            """,Long.class
            );
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    public LoginServiceImpl(UserMapper userMapper, StringRedisTemplate stringRedisTemplate, JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public LoginTokens login(String phone, String code) {
          Long verified=stringRedisTemplate.execute(
                  VERIFY_CODE,
                  List.of("m1kasa:login:code:"+phone),
                  code
          );
          if (!Long.valueOf(1).equals(verified)){
              return null;
          }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone)
        );
        if (user==null){
            user=new User();
            user.setPhone(phone);
            user.setPassword("");
            user.setNickName("user_"+
                    UUID.randomUUID().toString().substring(0,8));
            user.setIcon("");
            LocalDateTime now=LocalDateTime.now();
            user.setCreateTime(now);
            user.setUpdateTime(now);

            if (userMapper.insert(user)!=1){
                throw new IllegalStateException("用户创建失败");
            }
        }

        UUID sessionId = UUID.randomUUID();

        String accessToken = jwtTokenService.issue(user.getId(), sessionId);
        String refreshToken = refreshTokenService.create(user.getId(), sessionId);

        // login() 的最后一行
        return new LoginTokens(accessToken, refreshToken, RefreshTokenService.REFRESH_TTL);
    }

    private static final Pattern LEGACY_TOKEN = Pattern.compile("[0-9a-f]{32}");

    @Override
    public void logout(String token) {
        if (LEGACY_TOKEN.matcher(token).matches()) {
            stringRedisTemplate.delete("m1kasa:login:token:" + token);
            return;
        }

        JwtTokenService.VerifiedToken verified;
        try {
            //1.先验签
            verified = jwtTokenService.verify(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return;
        }
        if (verified.sessionId() != null) {
            refreshTokenService.revokeSession(verified.sessionId());
        }
        Duration remaining = Duration.between(Instant.now(), verified.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }

        Duration redisTtl = Duration.ofSeconds(remaining.toSeconds() + 1);
        stringRedisTemplate.opsForValue().set(verified.revocationKey(), "1", redisTtl);
    }

    @Override
    public LoginTokens refresh(String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken);
        if (rotated==null){
            return null;
        }
        String accessToken = jwtTokenService.issue(rotated.userId(), rotated.sessionId());
        if (!refreshTokenService.isActive(rotated.sessionId())) {
            return null;
        }
        return new LoginTokens(
                accessToken,
                rotated.refreshToken(),
                rotated.remaining()
        );
    }
}
