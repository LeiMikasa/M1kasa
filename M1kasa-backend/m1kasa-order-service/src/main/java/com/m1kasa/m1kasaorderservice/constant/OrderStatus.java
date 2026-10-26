package com.m1kasa.m1kasaorderservice.constant;

public final class OrderStatus {
    private OrderStatus() {

    }

    public static final int WAITING_PAYMENT = 1;
    public static final int PAID = 2;
    public static final int CANCELLED = 3;
}
