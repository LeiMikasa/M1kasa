package com.m1kasa.m1kasapayservice.config;

import com.m1kasa.m1kasapayservice.client.MockPaymentCallbackClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableFeignClients(clients = MockPaymentCallbackClient.class)
public class MockPaymentCallbackClientConfig {
}
