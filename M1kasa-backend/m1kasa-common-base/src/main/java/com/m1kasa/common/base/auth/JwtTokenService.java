package com.m1kasa.common.base.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Component
public final class JwtTokenService {

    private static final String ISSUER = "m1kasa-user-service";
    private static final String AUDIENCE = "m1kasa-gateway";
    public static final Duration ACCESS_TTL = Duration.ofMinutes(30);

    private final SecretKey key;
    private final JwtParser parser;
    private static final String SESSION_ID_CLAIM = "sid";
    public JwtTokenService(String base64Secret) {
        byte[] secret = Base64.getDecoder().decode(base64Secret);//准备密钥
        if (secret.length != 32) {
            throw new IllegalArgumentException("JWT 密钥用 Base64 解码后必须是 32 字节");
        }

        this.key = Keys.hmacShaKeyFor(secret); //验签器
        this.parser = Jwts.parser()  //配置解析器
                .keyLocator(header -> {
                    if (!"HS256".equals(header.getAlgorithm())) {
                        throw new JwtException("JWT 签名算法不匹配");
                    }
                    return key;
                })
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build();
    }
    //签发token
    public String issue(long userId) {
        return issue(userId, UUID.randomUUID());
    }

    public String issue(long userId, UUID sessionId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须大于 0");
        }

        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(Long.toString(userId))
                .claim(SESSION_ID_CLAIM, Objects.requireNonNull(sessionId).toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ACCESS_TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
    //验证token并返回字段
    public VerifiedToken verify(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        String jti = claims.getId();
        Date expiration = claims.getExpiration();

        if (jti == null || jti.isBlank() || expiration == null) {
            throw new JwtException("JWT 缺少 jti 或 exp");
        }

        long userId = Long.parseLong(claims.getSubject());
        if (userId <= 0) {
            throw new JwtException("JWT 用户 ID 无效");
        }

        String sid = claims.get(SESSION_ID_CLAIM, String.class);
        UUID sessionId = sid == null ? null : UUID.fromString(sid);

        return new VerifiedToken(userId, jti, expiration.toInstant(), sessionId);
    }

    public record VerifiedToken(
            long userId,
            String jti,
            Instant expiresAt,
            UUID sessionId
    ) {
        public String revocationKey() {
            return "m1kasa:login:revoked:" + jti;
        }
    }
    //黑名单key
    public static String sessionRevocationKey(UUID sessionId) {
        return "m1kasa:login:revoked-session:{" + sessionId + "}";
    }
}
