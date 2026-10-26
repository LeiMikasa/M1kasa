package com.m1kasa.order.api;

import java.time.LocalDateTime;

/** 当前登录用户的订单查询结果，ID 使用字符串避免 JavaScript 整数精度丢失。 */
public record OrderSummaryDTO(
        String id,
        String voucherId,
        Integer status,
        LocalDateTime createTime
) {
}
