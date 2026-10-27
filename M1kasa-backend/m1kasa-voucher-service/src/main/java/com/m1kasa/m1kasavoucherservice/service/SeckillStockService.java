package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;

public interface SeckillStockService {
    void deduct(SeckillOrderCreatedMessage message);
}
