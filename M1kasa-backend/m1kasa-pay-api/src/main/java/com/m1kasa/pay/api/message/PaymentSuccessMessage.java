package com.m1kasa.pay.api.message;

import java.time.LocalDateTime;

public record PaymentSuccessMessage(
        String eventId,
        String payNo,
        Long orderId,
        Long userId,
        Long payAmountFen,
        String channel,
        String channelTradeNo,
        LocalDateTime paidTime
) {
}
