package com.m1kasa.voucher.api.message;

/**
 * 秒杀创建订单消息。
 *
 * @param requestId 本次抢券请求的唯一标识
 * @param voucherId 优惠券 ID
 * @param userId    用户 ID
 */
public record SeckillOrderCreateMessage(
        String requestId,
        Long voucherId,
        Long userId
) {
}
