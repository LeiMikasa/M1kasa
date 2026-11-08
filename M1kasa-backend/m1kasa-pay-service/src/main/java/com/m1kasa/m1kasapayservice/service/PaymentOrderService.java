package com.m1kasa.m1kasapayservice.service;

import com.m1kasa.m1kasapayservice.entity.PaymentOrder;

public interface PaymentOrderService {

    PaymentOrder create(Long orderId, Long userId);
}
