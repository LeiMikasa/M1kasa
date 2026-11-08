package com.m1kasa.m1kasapayservice.service;

import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;

public interface PaymentResultService {

    void handleSuccess(PaymentSuccessResult result);
}
