package com.m1kasa.m1kasaorderservice.statemachine;

public enum OrderEvent {
    //库存扣减成功
    STOCK_DEDUCTED,
    //库存处理明确拒绝
    STOCK_REJECTED,

    PAYMENT_SUCCEEDED
}
