package com.m1kasa.m1kasashopservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ShopSearchExecutorConfig {

    @Bean("shopSearchOutboxExecutor")
    public ThreadPoolTaskExecutor shopSearchOutboxExecutor(){
        ThreadPoolTaskExecutor executor=
                new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("shop-search-outbox-");

        // 满了就拒绝，保留 Outbox，后续补发
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        return executor;
    }
}
