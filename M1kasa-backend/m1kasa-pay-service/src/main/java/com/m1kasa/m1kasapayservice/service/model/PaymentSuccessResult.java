package com.m1kasa.m1kasapayservice.service.model;

import java.time.LocalDateTime;

public record PaymentSuccessResult(
        String payNo,
        String channel,
        String channelTradeNo,
        Long payAmountFen,
        LocalDateTime paidTime
) {
}
