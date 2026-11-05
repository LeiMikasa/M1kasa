package com.m1kasa.m1kasavoucherservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SeckillOutboxExecutorConfig {

    @Bean("seckillOutboxExecutor")
    public ThreadPoolTaskExecutor seckillOutboxExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        // 最多使用 4 个工作线程
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);

        // 工作线程忙碌时，最多排队 200 个任务
        executor.setQueueCapacity(200);

        // 便于通过日志识别发送线程
        executor.setThreadNamePrefix("seckill-outbox-");

        // 线程和队列都满时，抛出任务拒绝异常
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        return executor;
    }
}
