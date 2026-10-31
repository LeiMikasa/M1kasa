package com.m1kasa.m1kasacontentservice.constant;

public final class BlogLikeMqConstants {
    private BlogLikeMqConstants(){

    }
    //点赞业务交换机，后续声明未direct类型
    public static final String EXCHANGE=
            "m1kasa.blog.like.exchange";

    //点赞状态变更消息的路由键
    public static final String ROUTING_KEY=
            "blog.like.changed";

    //点赞状态变更队列
    public static final String QUEUE=
            "m1kasa.blog.like.changed.queue";

    //点赞死信交换机
    public static final String DEAD_LETTER_EXCHANGE=
            "m1kasa.blog.like.dlx";

    //点赞死信路由键
    public static final String DEAD_LETTER_ROUTING_KEY=
            "blog.like.failed";

    //点赞死信队列
    public static final String DEAD_LETTER_QUEUE=
            "m1kasa.blog.like.dlq";
}
