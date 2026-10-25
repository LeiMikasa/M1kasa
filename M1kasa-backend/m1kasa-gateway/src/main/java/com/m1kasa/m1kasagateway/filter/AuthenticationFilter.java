package com.m1kasa.m1kasagateway.filter;

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

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String TOKEN_PREFIX = "m1kasa:login:token:";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final Duration TOKEN_TTL=Duration.ofMinutes(30);
    public AuthenticationFilter(
            ReactiveStringRedisTemplate redisTemplate
    ) {
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
        return redisTemplate.opsForValue()
                .get(TOKEN_PREFIX + token)
                // Redis 没查到 token，转成空字符串，统一在下面判断
                .defaultIfEmpty("")
                .flatMap(userId -> {
                    if (!StringUtils.hasText(userId)) {
                        return unauthorized(cleanExchange);
                    }

                    //查到有效登录态后，将剩余有效期重置为30分钟
                    return redisTemplate.expire(tokenKey,TOKEN_TTL)
                            .flatMap(renewed->{
                                //查询后，token可能刚好过期或被退出登录删除
                                if (!Boolean.TRUE.equals(renewed)){
                                    return unauthorized(cleanExchange);
                                }
                                // 用户 ID 来自 Redis，覆盖到发给下游的请求头
                                ServerHttpRequest authenticatedRequest = cleanExchange
                                        .getRequest()
                                        .mutate()
                                        .headers(headers ->
                                                headers.set(USER_ID_HEADER, userId))
                                        .build();

                                ServerWebExchange authenticatedExchange = cleanExchange
                                        .mutate()
                                        .request(authenticatedRequest)
                                        .build();

                                return chain.filter(authenticatedExchange);
                            });

                });
    }

    private boolean isPublicRequest(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method)) {
            return "/user/code".equals(path)
                    || "/user/login".equals(path);
        }

        if (HttpMethod.GET.equals(method)) {
            return "/shop-type/list".equals(path)
                    || path.startsWith("/shop/")
                    || path.matches("/voucher/list/[^/]+");
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
