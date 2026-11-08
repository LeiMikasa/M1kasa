package com.m1kasa.pay.api.constant;

public final class PaymentMqConstants {

    private PaymentMqConstants() {
    }

    public static final String EXCHANGE =
            "m1kasa.payment.exchange";

    public static final String SUCCESS_ROUTING_KEY =
            "payment.success";

    public static final String SUCCESS_QUEUE =
            "m1kasa.order.payment.success.queue";

    public static final String DEAD_LETTER_EXCHANGE =
            "m1kasa.payment.dlx";

    public static final String DEAD_LETTER_ROUTING_KEY =
            "payment.success.failed";

    public static final String DEAD_LETTER_QUEUE =
            "m1kasa.order.payment.success.dlq";
}
