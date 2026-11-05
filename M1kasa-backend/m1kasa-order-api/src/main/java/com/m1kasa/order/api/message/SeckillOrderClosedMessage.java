package com.m1kasa.order.api.message;

/**
 * 订单因库存明确拒绝而关闭，通知优惠券服务处理原请求的预占补偿。
 * 这里只描述已发生的关单，不代表库存已经回补完成。
 */
public record SeckillOrderClosedMessage(
        String requestId,
        Long orderId,
        Long voucherId,
        Long userId,
        String failureCode
) {
}
