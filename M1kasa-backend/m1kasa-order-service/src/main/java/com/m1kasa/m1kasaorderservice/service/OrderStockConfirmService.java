package com.m1kasa.m1kasaorderservice.service;

import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;

public interface OrderStockConfirmService {

    void confirm(SeckillStockDeductedMessage message, Long payAmountFen);
}
