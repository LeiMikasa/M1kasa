package com.m1kasa.m1kasapayservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasapayservice.constant.PaymentStatus;
import com.m1kasa.m1kasapayservice.entity.MockChannelTrade;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import com.m1kasa.m1kasapayservice.entity.PaymentTrade;
import com.m1kasa.m1kasapayservice.mapper.MockChannelTradeMapper;
import com.m1kasa.m1kasapayservice.mapper.PaymentOrderMapper;
import com.m1kasa.m1kasapayservice.mapper.PaymentTradeMapper;
import com.m1kasa.m1kasapayservice.service.MockPaymentChannelService;
import com.m1kasa.m1kasapayservice.service.MockChannelTradeWriter;
import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Profile("local")
public class MockPaymentChannelServiceImpl implements MockPaymentChannelService {
    private final PaymentOrderMapper paymentOrderMapper;
    private final MockChannelTradeMapper channelTradeMapper;
    private final MockChannelTradeWriter channelTradeWriter;

    public MockPaymentChannelServiceImpl(PaymentOrderMapper paymentOrderMapper, MockChannelTradeMapper channelTradeMapper, MockChannelTradeWriter channelTradeWriter) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.channelTradeMapper = channelTradeMapper;
        this.channelTradeWriter = channelTradeWriter;
    }


    @Override
    public MockChannelTrade confirm(String payNo, Long userId) {
        if (payNo==null||!payNo.matches("[0-9a-f]{32}")){
            throw new IllegalArgumentException("支付单号不合法");
        }
        if (userId==null||userId<=0){
            throw new IllegalArgumentException("用户ID不合法");
        }
        PaymentOrder payment = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getPayNo, payNo)
        );
        if (payment==null){
            throw new IllegalArgumentException("支付单不存在");
        }
        if (!userId.equals(payment.getUserId())){
            throw new IllegalArgumentException(
                    "支付单不属于当前用户"
            );
        }
        if (!"MOCK".equals(payment.getChannel())){
            throw new IllegalStateException(
                    "当前支付渠道不支持模拟付款"
            );
        }
        Long amountFen=payment.getPayAmountFen();
        if (amountFen==null||amountFen<=0){
            throw new IllegalStateException("支付金额不合法");
        }
        //模拟渠道已经收过款，返回原交易，不在重复收款
        MockChannelTrade existing = channelTradeMapper.selectById(payment.getPayNo());
        if (existing!=null){
            return verifyTrade(existing,amountFen);
        }
        if (!Objects.equals(
                payment.getStatus(),
                PaymentStatus.WAITING_PAYMENT
        )){
            throw new IllegalStateException("当前支付单不能发起付款");
        }
        try {
            return channelTradeWriter.recordSuccessfulTrade(
                    payment.getPayNo(),
                    amountFen
            );
        }catch (DuplicateKeyException ex) {
            // 并发确认：另一个请求可能已经记录收款成功
            existing = channelTradeMapper.selectById(
                    payment.getPayNo()
            );

            if (existing == null) {
                throw ex;
            }

            return verifyTrade(existing, amountFen);
        }
    }

    @Override
    public PaymentSuccessResult querySuccess(String payNo) {
        if (payNo==null||!payNo.matches("[0-9a-f]{32}")){
           throw new IllegalArgumentException("支付单号不合法");
        }
        //查询模拟渠道自己的收款记录
        MockChannelTrade trade = channelTradeMapper.selectById(payNo);
        if (trade==null){
            return null;
        }
        return new PaymentSuccessResult(
                trade.getPayNo(),
                "MOCK",
                trade.getChannelTradeNo(),
                trade.getPayAmountFen(),
                trade.getPaidTime()
        );
    }

    private MockChannelTrade verifyTrade(
            MockChannelTrade trade,
            Long expectedAmountFen
    ){
        if (!expectedAmountFen.equals(trade.getPayAmountFen())
        || trade.getChannelTradeNo()==null
        || trade.getChannelTradeNo().isBlank()
        || trade.getPaidTime()==null){
            throw new IllegalStateException("模拟渠道交易记录异常");
        }
        return trade;
    }
}
