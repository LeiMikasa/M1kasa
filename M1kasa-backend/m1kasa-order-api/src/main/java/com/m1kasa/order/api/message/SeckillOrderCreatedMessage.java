package com.m1kasa.order.api.message;

/*
* 秒杀订单创建成功事件
* */


public record SeckillOrderCreatedMessage(
        String requestId,
        Long orderId,
        Long voucherId,
        Long userId
) {
}
