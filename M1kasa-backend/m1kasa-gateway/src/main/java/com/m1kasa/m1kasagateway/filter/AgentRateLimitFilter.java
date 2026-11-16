package com.m1kasa.m1kasagateway.filter;

import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisException;
import org.redisson.api.RRateLimiterReactive;
import org.redisson.api.RateType;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import java.time.Duration;

@Component
public class AgentRateLimitFilter implements GlobalFilter, Ordered {
    private static final String AGENT_ROUTE_ID="agent-service";
    private static final String KEY_PREFIX="m1kasa:rate:agent:chat";
    private static final int RATE=5;
    private static final Duration INTERVAL=Duration.ofMinutes(1);
    private static final Duration KEEP_ALIVE=Duration.ofMinutes(10);
    private final RedissonReactiveClient redisson;

    public AgentRateLimitFilter(RedissonClient client) {
        this.redisson = client.reactive();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route==null || !AGENT_ROUTE_ID.equals(route.getId())){
          return chain.filter(exchange);
        }
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId==null||userId.isBlank()){
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        RRateLimiterReactive limiter = redisson.getRateLimiter(KEY_PREFIX + userId);
        //1.初始化限流规则
        Mono<Boolean> initResult = limiter.trySetRate(
                RateType.OVERALL,
                RATE,
                INTERVAL,
                KEEP_ALIVE
        );
        //2.配置检查完成后，申请许可证
        Mono<Boolean> permitResult = initResult
                .flatMap(created -> limiter.tryAcquire())
                .onErrorMap(RedisException.class, error -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "限流服务暂时不可用",
                        error
                ));
        //3.根据领00到的结果，决定是否把请求交给Agent
        return permitResult.flatMap(allowed->{
            if (allowed){
                return chain.filter(exchange);
            }
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        });
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
