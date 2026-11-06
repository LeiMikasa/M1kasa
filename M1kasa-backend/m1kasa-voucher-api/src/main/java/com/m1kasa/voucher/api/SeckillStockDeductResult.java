package com.m1kasa.voucher.api;

public record SeckillStockDeductResult(
        Outcome outcome,
        String failureCode
) {
    public enum Outcome {
        DEDUCTED,
        REJECTED
    }
}
