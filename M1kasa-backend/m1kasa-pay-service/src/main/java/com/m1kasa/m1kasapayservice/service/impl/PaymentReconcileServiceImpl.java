package com.m1kasa.m1kasapayservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasapayservice.constant.PaymentStatus;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import com.m1kasa.m1kasapayservice.mapper.PaymentOrderMapper;
import com.m1kasa.m1kasapayservice.service.MockPaymentChannelService;
import com.m1kasa.m1kasapayservice.service.PaymentReconcileService;
import com.m1kasa.m1kasapayservice.service.PaymentResultService;
import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("local")
public class PaymentReconcileServiceImpl
        implements PaymentReconcileService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final MockPaymentChannelService channelService;
    private final PaymentResultService paymentResultService;

    public PaymentReconcileServiceImpl(
            PaymentOrderMapper paymentOrderMapper,
            MockPaymentChannelService channelService,
            PaymentResultService paymentResultService
    ) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.channelService = channelService;
        this.paymentResultService = paymentResultService;
    }

    @Override
    public boolean reconcile(String payNo) {
        if (payNo == null || !payNo.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("支付单号不合法");
        }

        // 1. 查询我方业务支付单
        PaymentOrder payment = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPayNo, payNo)
        );

        if (payment == null) {
            throw new IllegalArgumentException("支付单不存在");
        }

        // 2. 我方已经处理成功，无需再次查渠道
        if (Objects.equals(
                payment.getStatus(),
                PaymentStatus.PAID
        )) {
            return true;
        }

        if (!"MOCK".equals(payment.getChannel())) {
            throw new IllegalStateException(
                    "当前查单实现只支持模拟渠道"
            );
        }

        if (!Objects.equals(
                payment.getStatus(),
                PaymentStatus.WAITING_PAYMENT
        )) {
            throw new IllegalStateException(
                    "当前支付单状态不支持此查单处理"
            );
        }

        // 3. 主动查询渠道，获取可信的成功结果
        PaymentSuccessResult result =
                channelService.querySuccess(payment.getPayNo());

        if (result == null) {
            // 查询期间，回调可能已经处理完成，再读一次本地状态
            PaymentOrder latest =
                    paymentOrderMapper.selectById(payment.getId());

            if (latest == null) {
                throw new IllegalStateException("支付单已不存在");
            }

            return Objects.equals(
                    latest.getStatus(),
                    PaymentStatus.PAID
            );
        }

        // 4. 与成功回调复用同一套幂等处理
        // 返回前完成支付单、业务流水和 Outbox 的事务提交
        paymentResultService.handleSuccess(result);

        return true;
    }
}
