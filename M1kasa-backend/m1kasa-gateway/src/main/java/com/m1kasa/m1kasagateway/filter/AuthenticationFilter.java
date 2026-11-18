package com.m1kasa.m1kasagateway.filter;

import com.m1kasa.common.base.auth.JwtTokenService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import io.jsonwebtoken.JwtException;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {
    private static final Pattern LEGACY_TOKEN = Pattern.compile("[0-9a-f]{32}");
    private static final String TOKEN_PREFIX = "m1kasa:login:token:";
    private static final String USER_ID_HEADER = "X-User-Id";
    private final JwtTokenService jwtTokenService;
    private final ReactiveStringRedisTemplate redisTemplate;
    public AuthenticationFilter(
            JwtTokenService jwtTokenService, ReactiveStringRedisTemplate redisTemplate
    ) {
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        // 清除客户端自己传来的用户 ID，防止冒充其他用户
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.remove(USER_ID_HEADER))
                .build();

        ServerWebExchange cleanExchange = exchange.mutate()
                .request(request)
                .build();

        String path = request.getPath().pathWithinApplication().value();
        HttpMethod method = request.getMethod();

        if (isPublicRequest(method, path)) {
            return chain.filter(cleanExchange);
        }

        String authorization = request.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith("Bearer ")) {
            return unauthorized(cleanExchange);
        }

        String token = authorization
                .substring("Bearer ".length())
                .trim();

        if (!StringUtils.hasText(token)) {
            return unauthorized(cleanExchange);
        }
        String tokenKey=TOKEN_PREFIX+token;
        if (LEGACY_TOKEN.matcher(token).matches()) {
            return redisTemplate.opsForValue()
                    .get(TOKEN_PREFIX + token)
                    .defaultIfEmpty("")
                    .flatMap(userId -> StringUtils.hasText(userId)
                            ? forwardWithUserId(cleanExchange, chain, userId)
                            : unauthorized(cleanExchange));
        }

        JwtTokenService.VerifiedToken verified;
        try {
            verified = jwtTokenService.verify(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(cleanExchange);
        }
        //网关同时检查jti和jti
        List<String> revocationKeys=verified.sessionId()==null
                ? List.of(verified.revocationKey())
                : List.of(
                        verified.revocationKey(),
                        JwtTokenService.sessionRevocationKey(verified.sessionId())
        );
        return redisTemplate.countExistingKeys(revocationKeys)
                .flatMap(count -> count > 0
                        ? unauthorized(cleanExchange)
                        : forwardWithUserId(
                        cleanExchange, chain, Long.toString(verified.userId())
                ));

    }
    private Mono<Void> forwardWithUserId(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String userId
    ) {
        ServerHttpRequest authenticatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(USER_ID_HEADER, userId))
                .build();

        return chain.filter(exchange.mutate()
                .request(authenticatedRequest)
                .build());
    }

    private boolean isPublicRequest(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method)) {
            return "/user/code".equals(path)
                    || "/user/login".equals(path)
                    || "/pay/callbacks/mock".equals(path)
                    || "/user/refresh".equals(path);
        }

        if (HttpMethod.GET.equals(method)) {
            return "/shop-type/list".equals(path)
                    || path.startsWith("/shop/")
                    || path.matches("/voucher/list/[^/]+")
                    //作者公开资料
                    || "/user/public/batch".equals(path)
                    //笔记广场
                    || "/blog/feed".equals(path)
                    //笔记详情，ID必须由数字组成
                    || path.matches("/blog/[0-9]+")
                    || path.matches("/user/public/[0-9]+")
                    // 指定作者的公开笔记分页
                    || path.matches("/blog/of/user/[0-9]+")
                    || "/search/shops".equals(path)
                    || path.matches("/blog/[0-9]+/comments")
                    || path.matches("/blog/[0-9]+/comments/[0-9]+/replies")
                    ;
        }

        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            var response = exchange.getResponse();

            // HTTP 状态码，前端通过它识别登录失效
            response.setStatusCode(HttpStatus.UNAUTHORIZED);

            // 告诉前端响应正文是 JSON
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String body = """
        {
          "success": false,
          "errorMsg": "未登录或登录已过期，请重新登录",
          "data": null,
          "total": null
        }
        """;

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer));
        });
    }

    @Override
    public int getOrder() {
        // 数值越小，优先级越高
        return -100;
    }
}
