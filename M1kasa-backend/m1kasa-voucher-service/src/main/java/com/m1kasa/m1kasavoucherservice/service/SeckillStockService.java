package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.SeckillStockDeductRequest;
import com.m1kasa.voucher.api.SeckillStockDeductResult;

public interface SeckillStockService {
    void deduct(SeckillOrderCreatedMessage message);

    SeckillStockDeductResult deductForRpc(SeckillStockDeductRequest request);
}
