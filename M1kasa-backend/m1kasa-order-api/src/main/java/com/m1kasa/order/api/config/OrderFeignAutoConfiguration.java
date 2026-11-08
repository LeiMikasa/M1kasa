package com.m1kasa.order.api.config;

import com.m1kasa.order.api.client.OrderPaymentClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration(
        afterName = "org.springframework.cloud.openfeign.FeignAutoConfiguration"
)
@ConditionalOnClass(name = {
        "feign.Feign",
        "org.springframework.cloud.openfeign.FeignAutoConfiguration"
})
@EnableFeignClients(clients = OrderPaymentClient.class)
public class OrderFeignAutoConfiguration {
}
