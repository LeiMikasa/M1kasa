package com.m1kasa.voucher.api;

public record SeckillStockDeductResult(
        Outcome outcome,
        String failureCode,
        Long payAmountFen
) {
    public enum Outcome {
        DEDUCTED,
        REJECTED
    }
}
