package com.m1kasa.m1kasaorderservice.service;

import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;

public interface OrderService {
    Long createSeckillOrder(SeckillOrderCreateMessage message);
}
