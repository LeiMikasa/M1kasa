package com.m1kasa.user.api.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration(
        afterName = "org.springframework.cloud.openfeign.FeignAutoConfiguration"
)
@ConditionalOnClass(name = "feign.Feign")
@EnableFeignClients(
        basePackages = "com.m1kasa.user.api.client"
)
public class UserFeignAutoConfiguration {
}
