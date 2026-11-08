package com.m1kasa.m1kasapayservice.controller;

import com.m1kasa.common.auth.annotation.AllowAnonymous;
import com.m1kasa.m1kasapayservice.service.PaymentResultService;
import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import com.m1kasa.m1kasapayservice.support.MockPaymentSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
public class MockPaymentCallbackController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    MockPaymentCallbackController.class
            );

    private final MockPaymentSignature signatureVerifier;
    private final PaymentResultService paymentResultService;

    public MockPaymentCallbackController(
            MockPaymentSignature signatureVerifier,
            PaymentResultService paymentResultService
    ) {
        this.signatureVerifier = signatureVerifier;
        this.paymentResultService = paymentResultService;
    }

    @AllowAnonymous
    @PostMapping(
            value = "/pay/callbacks/mock",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> receive(
            @RequestBody PaymentSuccessResult result,
            @RequestHeader(
                    value = "X-Mock-Pay-Signature",
                    required = false
            ) String signature
    ) {
        try {
            // 1. 验证渠道签名
            if (!signatureVerifier.verify(result, signature)) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body("fail");
            }

            // 2. 更新支付单并保存 Outbox
            // 通过另一个 Bean 调用，返回前完成事务提交
            paymentResultService.handleSuccess(result);

            // 3. 提交成功之后，才确认回调处理成功
            return ResponseEntity.ok("success");

        } catch (IllegalArgumentException ex) {
            log.warn("模拟支付回调参数不合法", ex);

            return ResponseEntity.badRequest().body("fail");

        } catch (RuntimeException ex) {
            log.error("处理模拟支付回调失败", ex);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("fail");
        }
    }
}
