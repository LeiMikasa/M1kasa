package com.m1kasa.order.api;

import java.util.List;

public record DeadLetterPageDTO(
        List<DeadLetterSummaryDTO> records,
        long total,
        int current,
        int size
) {
}
