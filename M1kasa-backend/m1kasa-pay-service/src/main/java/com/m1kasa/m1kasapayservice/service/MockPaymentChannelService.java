package com.m1kasa.m1kasapayservice.service;

import com.m1kasa.m1kasapayservice.entity.MockChannelTrade;
import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;

public interface MockPaymentChannelService {

    MockChannelTrade confirm(String payNo, Long userId);

    PaymentSuccessResult querySuccess(String payNo);
}
