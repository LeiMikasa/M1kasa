package com.m1kasa.m1kasaorderservice.service;

import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;

public interface OrderStockRejectService {
    void reject(SeckillStockRejectedMessage message);
}
