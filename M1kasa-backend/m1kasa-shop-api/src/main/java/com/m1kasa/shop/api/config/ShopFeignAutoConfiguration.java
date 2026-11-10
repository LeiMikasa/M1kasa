package com.m1kasa.shop.api.config;

import com.m1kasa.shop.api.client.ShopClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration(
        afterName =
                "org.springframework.cloud.openfeign.FeignAutoConfiguration"
)
@ConditionalOnClass(name = {
        "feign.Feign",
        "org.springframework.cloud.openfeign.FeignClient"
})
@EnableFeignClients(clients = ShopClient.class)
public class ShopFeignAutoConfiguration {
}
