package com.m1kasa.m1kasaorderservice.interceptor;

import com.m1kasa.common.base.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final String TOKEN_PREFIX = "m1kasa:login:token:";

    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        UserContext.clear();
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")) {
            return unauthorized(response);
        }

        String token = authorization.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            return unauthorized(response);
        }

        // 用户身份只来自 Redis 登录态，忽略客户端传来的 X-User-Id。
        String storedUserId = stringRedisTemplate.opsForValue()
                .get(TOKEN_PREFIX + token);
        if (!StringUtils.hasText(storedUserId)) {
            return unauthorized(response);
        }

        try {
            long userId = Long.parseLong(storedUserId);
            if (userId <= 0) {
                return unauthorized(response);
            }
            UserContext.setUserId(userId);
            return true;
        } catch (NumberFormatException ex) {
            return unauthorized(response);
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        UserContext.clear();
    }

    private boolean unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("""
                {"success":false,"errorMsg":"未登录或登录已过期，请重新登录","data":null,"total":null}
                """);
        return false;
    }
}
