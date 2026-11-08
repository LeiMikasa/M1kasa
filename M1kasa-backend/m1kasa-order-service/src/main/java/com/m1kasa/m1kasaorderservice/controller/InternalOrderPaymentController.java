package com.m1kasa.m1kasaorderservice.controller;

import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.service.OrderQueryService;
import com.m1kasa.order.api.OrderPaymentInfoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/order")
public class InternalOrderPaymentController {
    private final OrderQueryService orderQueryService;
    private final String rpcToken;

    public InternalOrderPaymentController(
            OrderQueryService orderQueryService,
            @Value("${m1kasa.order.payment-rpc.token}") String rpcToken) {
        this.orderQueryService = orderQueryService;
        this.rpcToken = rpcToken;
    }

    @GetMapping("/{orderId}/payable")
    public OrderPaymentInfoDTO findPayable(
            @PathVariable("orderId") Long orderId,
            @RequestParam("userId") Long userId,
            @RequestHeader(value = "X-Order-Payment-Token", required = false)
            String token
    ){
        if (!StringUtils.hasText(rpcToken)
                || token == null
                || !MessageDigest.isEqual(
                rpcToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "支付查单鉴权失败");
        }

        OrderPaymentInfoDTO order;
        try {
            order = orderQueryService.findPaymentInfo(orderId, userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在");
        }

        if (!Integer.valueOf(OrderStatus.WAITING_PAYMENT).equals(order.status())
                || order.payAmountFen() == null
                || order.payAmountFen() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前订单不可支付");
        }

        return order;
    }

}
