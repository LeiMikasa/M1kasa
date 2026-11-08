package com.m1kasa.m1kasapayservice.client;

import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "mock-payment-callback",
        url = "${m1kasa.pay.mock.callback-base-url:http://localhost:8080}"
)
public interface MockPaymentCallbackClient {
    @PostMapping(
         value = "/pay/callbacks/mock",
         consumes = MediaType.APPLICATION_JSON_VALUE,
         produces = MediaType.TEXT_PLAIN_VALUE
    )
    String notifyPaid(
            @RequestBody PaymentSuccessResult result,
            @RequestHeader("X-Mock-Pay-Signature")
            String signature
            );

}
