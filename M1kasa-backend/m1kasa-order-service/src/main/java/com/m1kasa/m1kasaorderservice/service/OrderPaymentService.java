package com.m1kasa.m1kasaorderservice.service;

import com.m1kasa.pay.api.message.PaymentSuccessMessage;

public interface OrderPaymentService {
    void confirmPaid(PaymentSuccessMessage message);
}
