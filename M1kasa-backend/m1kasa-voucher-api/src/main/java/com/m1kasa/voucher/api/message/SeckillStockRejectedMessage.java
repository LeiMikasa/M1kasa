package com.m1kasa.voucher.api.message;

/**
 * 秒杀库存处理明确拒绝事件。
 *
 * @param requestId   抢券请求 ID
 * @param orderId     订单 ID
 * @param voucherId   优惠券 ID
 * @param userId      用户 ID
 * @param failureCode 拒绝原因代码
 */
public record SeckillStockRejectedMessage(
        String requestId,
        Long orderId,
        Long voucherId,
        Long userId,
        String failureCode
) {
}
