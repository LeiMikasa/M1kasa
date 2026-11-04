package com.m1kasa.m1kasaorderservice.constant;

public final class OrderStatus {

    private OrderStatus() {
    }

    // 订单已创建，等待优惠券服务确认库存
    public static final int WAITING_STOCK = 0;

    // 库存已确认，等待支付
    public static final int WAITING_PAYMENT = 1;

    // 已支付
    public static final int PAID = 2;

    // 已取消
    public static final int CANCELLED = 3;
}
