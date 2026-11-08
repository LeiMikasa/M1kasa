package com.m1kasa.m1kasapayservice.web;

import java.time.LocalDateTime;

public record PaymentOrderDTO(
        String payNo,
        String orderId,
        Long payAmountFen,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime paidTime
) {
}
