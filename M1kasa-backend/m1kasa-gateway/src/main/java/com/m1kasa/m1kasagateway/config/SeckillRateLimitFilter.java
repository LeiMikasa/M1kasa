package com.m1kasa.m1kasagateway.config;

import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class SeckillRateLimitFilter implements GlobalFilter, Ordered {
    //规则名字
    private static final String RESOURCE = "seckill-reserve-by-voucher";

    private static final PathPattern RESERVE_PATH = PathPatternParser.defaultInstance
            .parse("/voucher/seckill/{voucherId}/reserve");

    private final int permitsPerSecond;

    public SeckillRateLimitFilter(
            @Value("${SECKILL_RATE_LIMIT_PERMITS:100}") int permitsPerSecond
    ) {
        this.permitsPerSecond = permitsPerSecond;
    }

    @PostConstruct
    public void registerRule() {
        //启动时告诉Sentinel按什么限制
        ParamFlowRule rule = new ParamFlowRule(RESOURCE)
                .setParamIdx(0)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(permitsPerSecond)
                .setDurationInSec(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);

        ParamFlowRuleManager.loadRules(List.of(rule));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();

        if (!HttpMethod.POST.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        var match = RESERVE_PATH.matchAndExtract(request.getPath().pathWithinApplication());
        if (match == null) {
            return chain.filter(exchange);
        }

        long voucherId;
        try {
            voucherId = Long.parseLong(match.getUriVariables().get("voucherId"));
        } catch (NumberFormatException ex) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "优惠券 ID 无效", ex
            ));
        }

        if (voucherId <= 0) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "优惠券 ID 无效"
            ));
        }

        return Mono.using(
                () -> SphU.asyncEntry(RESOURCE, EntryType.IN, 1, voucherId),
                entry -> chain.filter(exchange),
                entry -> entry.exit(1, voucherId)
        ).onErrorResume(BlockException.class, ex -> tooManyRequests(exchange));
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
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
        return -110;
    }
}
