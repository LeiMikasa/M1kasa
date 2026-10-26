package com.m1kasa.m1kasavoucherservice.enums;

public enum SeckillReserveStatus {
    RESERVED, //获得资格
    SOLD_OUT, //库存不足
    DUPLICATE,//重复领取
    NOT_STARTED,//尚未开始
    ENDED,//已经结束
    NOT_INITIALIZED //活动未初始化或者数据不完整
}
