package com.m1kasa.m1kasavoucherservice.constant;

public final class SeckillMqConstants {
    private SeckillMqConstants(){

    }
    //交换机：接收生产者发来的消息
    public static final String EXCHANGE="m1kasa.seckill.exchange";
    //队列：保存等待创建订单的消息
    public static final String ORDER_QUEUE="m1kasa.seckill.order.queue";
    //路由键
    public static final String ORDER_ROUTING_KEY="seckill.order.create";
}
