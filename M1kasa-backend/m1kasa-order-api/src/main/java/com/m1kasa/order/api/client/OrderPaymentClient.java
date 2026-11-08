package com.m1kasa.order.api.client;

import com.m1kasa.order.api.OrderPaymentInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "m1kasa-order-service")
public interface OrderPaymentClient {
    @GetMapping("/internal/order/{orderId}/payable")
    OrderPaymentInfoDTO findPayable(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId
    );
}
