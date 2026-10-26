package com.m1kasa.m1kasavoucherservice.constant;

public final class SeckillRedisKeys {
    private SeckillRedisKeys(){

    }

    public static String activity(Long voucherId){
        return "m1kasa:seckill:activity:{"+voucherId+"}";
    }

    //增加领取记录的redis key
    public static String buyers(Long voucherId){
        return "m1kasa:seckill:buyers:{"+voucherId+"}";
    }
    //两个key的{10}相同，之后使用redis cluster时，可以落到同一个槽位，供同一个lua脚本操作

    /*
    * Key：m1kasa:seckill:outbox:{10}

          Hash 字段                   Hash 值
          request-001                 2474
          request-002                 2475
    *
    *
    *
    * */
    public static String outbox(Long voucherId){
        return "m1kasa:seckill:outbox:{"+voucherId+"}";
    }

}
