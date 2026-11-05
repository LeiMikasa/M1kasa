package com.m1kasa.order.api.constant;


public final class OrderMqConstants {
    public static final String EXCHANGE=
            "m1kasa.order.exchange";

    public static final String SECKILL_ORDER_CREATED_ROUTING_KEY
            ="seckill.order.created";

    // 订单因库存拒绝而关闭，触发预占补偿
    public static final String SECKILL_ORDER_CLOSED_ROUTING_KEY =
            "seckill.order.closed";

    private OrderMqConstants(){

    }
}
