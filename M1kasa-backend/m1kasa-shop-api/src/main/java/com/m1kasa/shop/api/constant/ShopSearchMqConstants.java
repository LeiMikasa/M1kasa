package com.m1kasa.shop.api.constant;

public final class ShopSearchMqConstants {
    //交换机
    public static final String EXCHANGE=
            "m1kasa.shop.exchange";
    //路由键
    public static final String ROUTING_KEY=
            "shop.search.sync";
    public static final String QUEUE=
            "m1kasa.shop.search.sync.queue";
    //死信交换机
    public static final String DEAD_LETTER_EXCHANGE=
            "m1kasa.shop.search.dlx";
    //死信路由键
    public static final String DEAD_LETTER_ROUTING_KEY=
            "shop.search.failed";
    //死信队列，保存消费失败的消息
    public static final String DEAD_LETTER_QUEUE=
            "m1kasa.shop.search.dlq";
    private ShopSearchMqConstants(){

    }
}
