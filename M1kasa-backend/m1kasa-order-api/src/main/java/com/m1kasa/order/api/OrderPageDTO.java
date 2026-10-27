package com.m1kasa.order.api;

import java.util.List;

public record OrderPageDTO(
        List<OrderSummaryDTO> records,
        long total,
        int current,
        int size
) {
}
