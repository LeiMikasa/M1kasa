package com.m1kasa.m1kasagateway.config;

import org.redisson.api.RRateLimiterReactive;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.NumberUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class SeckillRateLimitFilter implements GlobalFilter, Ordered {
    private static final PathPattern RESERVE_PATH = PathPatternParser.defaultInstance
            .parse("/voucher/seckill/{voucherId}/reserve");

    private final RedissonReactiveClient redisson;
    private final int permitsPerSecond;

    public SeckillRateLimitFilter(
            RedissonClient client,
            @Value("${SECKILL_RATE_LIMIT_PERMITS:100}") int permitsPerSecond
    ){
        this.redisson=client.reactive();
        this.permitsPerSecond=permitsPerSecond;
    }


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request=exchange.getRequest();
        //1.找到抢劵请求，并提取劵id
        if (!HttpMethod.POST.equals(request.getMethod())){
            return chain.filter(exchange);
        }
        var match=RESERVE_PATH.matchAndExtract(request.getPath().pathWithinApplication());
        if (match == null){
            return chain.filter(exchange);
        }
        long voucherId;
        try {
            String rawId=match.getUriVariables().get("voucherId");
            voucherId= NumberUtils.parseNumber(rawId,Long.class);
        }catch (NumberFormatException ex){
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,"优惠劵ID无效",ex
            ));
        }
        if (voucherId<=0){
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,"优惠劵ID无效"
            ));
        }
        //2.同一张劵的所有请求都用一个限流器
        String key="m1kasa:rate:seckill:voucher:"+voucherId;
        RRateLimiterReactive limiter = redisson.getRateLimiter(key);
        Mono<Boolean> initResult = limiter.trySetRate(
                RateType.OVERALL,
                permitsPerSecond,
                Duration.ofSeconds(1),
                Duration.ofMinutes(1)
        );
        Mono<Boolean> permitResult = initResult
                .flatMap(created -> limiter.tryAcquire())
                .onErrorMap(RedisException.class, error -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试"
                ));
        //3.拿到许可证就转发,额度不足就返回429
        return permitResult.flatMap(
                allowed->{
                    if (allowed){
                        return chain.filter(exchange);
                    }
                    return tooManyRequests(exchange);
                }
        );
    }
    private Mono<Void> tooManyRequests(ServerWebExchange exchange){
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = """
                {"success":false,"errorMsg":"抢券操作太频繁，请稍后再试"}
                """.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
