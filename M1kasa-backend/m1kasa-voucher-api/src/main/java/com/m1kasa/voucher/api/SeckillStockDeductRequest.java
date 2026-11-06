package com.m1kasa.voucher.api;
/*
*
* */
public record SeckillStockDeductRequest(
        String requestId,
        Long orderId,
        Long voucherId,
        Long userId
) {
}
