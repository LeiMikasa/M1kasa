package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.order.api.message.SeckillOrderClosedMessage;

public interface SeckillStockCompensationService {

    void compensate(SeckillOrderClosedMessage message);
}
