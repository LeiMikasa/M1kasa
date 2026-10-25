package com.m1kasa.voucher.api;

import java.time.LocalDateTime;

public record VoucherDTO(
        Long id,
        Long shopId,
        String title,
        String subTitle,
        String rules,
        //实际支付金额,单位分
        Long payValue,
        //可抵扣金额，单位分
        Long actualValue,
        //0:普通劵 1:秒杀劵
        Integer type,
        //1:上架 2:下架3：过期
        Integer status,
        //秒杀劵的库存以及活动时间，普通劵可以为null
        Integer stock,
        LocalDateTime beginTime,
        LocalDateTime endTime
) {
}
