package com.m1kasa.m1kasashopservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(DataRedisProperties properties){
        Config config=new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase());
        if (StringUtils.hasLength(properties.getUsername())) {
            server.setUsername(properties.getUsername());
        }
        if (StringUtils.hasLength(properties.getPassword())) {
            server.setPassword(properties.getPassword());
        }

        return Redisson.create(config);
    }

}
