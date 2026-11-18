package com.m1kasa.m1kasauserservice.service;

import com.m1kasa.common.base.auth.JwtTokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private static final DefaultRedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>(
            """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return nil
            end
    
            local separator = string.find(value, ':', 1, true)
            if not separator or string.sub(value, separator + 1) ~= ARGV[1] then
                return nil
            end
    
            local remainingMs = redis.call('PTTL', KEYS[1])
            if remainingMs <= 0 then
                return nil
            end
    
            local userId = string.sub(value, 1, separator - 1)
            redis.call('SET', KEYS[1], userId .. ':' .. ARGV[2], 'KEEPTTL')
            return userId .. ':' .. remainingMs
            """,
            String.class
    );

    public static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(JwtTokenService jwtTokenService, StringRedisTemplate redisTemplate) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    public String create(long userId, UUID sessionId) {
        if (userId <= 0 || sessionId == null) {
            throw new IllegalArgumentException("刷新会话参数无效");
        }
        //计算摘要写进redis
        String secret = generateSecret();
        String value = userId + ":" + hash(secret);

        Boolean created = redisTemplate.opsForValue()
                .setIfAbsent(redisKey(sessionId), value, REFRESH_TTL);
        if (!Boolean.TRUE.equals(created)) {
            throw new IllegalStateException("无法创建刷新会话");
        }

        return sessionId + "." + secret;
    }
     //生成随机串
    private static String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static String redisKey(UUID sessionId) {
        return "m1kasa:login:refresh:{" + sessionId + "}";
    }

    public RotatedToken rotate(String token) {
        if (token == null) {
            return null;
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2 || parts[1].isEmpty()) {
            return null;
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        String nextSecret = generateSecret();
        String result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(redisKey(sessionId)),
                hash(parts[1]),
                hash(nextSecret)
        );
        if (result == null) {
            return null;
        }

        String[] state = result.split(":", 2);
        return new RotatedToken(
                Long.parseLong(state[0]),
                sessionId,
                sessionId + "." + nextSecret,
                Duration.ofMillis(Long.parseLong(state[1]))
        );
    }

    public record RotatedToken(
            long userId,
            UUID sessionId,
            String refreshToken,
            Duration remaining
    ) {
    }
    //
    private static final DefaultRedisScript<Long> REVOKE_SESSION_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
                    redis.call('DEL', KEYS[1])
                    return 1
                    """,
                    Long.class
            );

    public void revokeSession(UUID sessionId) {
        Duration ttl = JwtTokenService.ACCESS_TTL.plusSeconds(1);

        redisTemplate.execute(
                REVOKE_SESSION_SCRIPT,
                List.of(
                        redisKey(sessionId),
                        JwtTokenService.sessionRevocationKey(sessionId)
                ),
                Long.toString(ttl.toMillis())
        );
    }

    public boolean isActive(UUID sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(sessionId)));
    }
}
