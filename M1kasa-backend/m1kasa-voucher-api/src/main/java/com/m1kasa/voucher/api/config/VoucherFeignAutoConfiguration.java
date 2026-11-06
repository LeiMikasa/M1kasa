package com.m1kasa.voucher.api.config;

import com.m1kasa.voucher.api.client.VoucherStockClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;

@AutoConfiguration(afterName = "org.springframework.cloud.openfeign.FeignAutoConfiguration")
@ConditionalOnClass(name = {
        "feign.Feign",
        "org.springframework.cloud.openfeign.FeignAutoConfiguration"
})
@EnableFeignClients(clients = VoucherStockClient.class)
public class VoucherFeignAutoConfiguration {
}
