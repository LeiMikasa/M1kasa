package com.m1kasa.voucher.api.message;

/**
 * 秒杀库存扣减成功事件。
 *
 * @param requestId 本次抢券请求 ID
 * @param orderId   订单 ID
 * @param voucherId 优惠券 ID
 * @param userId    用户 ID
 */
public record SeckillStockDeductedMessage(
        String requestId,
        Long orderId,
        Long voucherId,
        Long userId
) {
}
