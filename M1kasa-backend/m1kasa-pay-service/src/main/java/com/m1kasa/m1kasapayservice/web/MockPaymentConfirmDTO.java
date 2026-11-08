package com.m1kasa.m1kasapayservice.web;

import java.time.LocalDateTime;

public record MockPaymentConfirmDTO(
        String payNo,
        String channelTradeNo,
        Long payAmountFen,
        LocalDateTime paidTime
) {
}
