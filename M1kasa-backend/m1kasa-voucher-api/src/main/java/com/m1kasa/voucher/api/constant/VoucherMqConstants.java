package com.m1kasa.voucher.api.constant;

public final class VoucherMqConstants {

    private VoucherMqConstants() {
    }

    // 优惠券服务发布事件的交换机
    public static final String EXCHANGE =
            "m1kasa.voucher.exchange";

    // 库存扣减成功事件的路由键
    public static final String STOCK_DEDUCTED_ROUTING_KEY =
            "seckill.stock.deducted";

    // 库存处理明确拒绝事件
    public static final String STOCK_REJECTED_ROUTING_KEY =
            "seckill.stock.rejected";
}
