package com.m1kasa.order.api;

public record OrderPaymentInfoDTO(
        Long orderId,
        Long userId,
        Long payAmountFen,
        Integer status
) {
}
