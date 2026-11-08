package com.m1kasa.m1kasapayservice.service;

import com.m1kasa.m1kasapayservice.entity.MockChannelTrade;
import com.m1kasa.m1kasapayservice.entity.PaymentTrade;
import com.m1kasa.m1kasapayservice.mapper.MockChannelTradeMapper;
import com.m1kasa.m1kasapayservice.mapper.PaymentTradeMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
/*
* mock支付流水表
* */
@Service
@Profile("local")
public class MockChannelTradeWriter {
    private final MockChannelTradeMapper channelTradeMapper;

    public MockChannelTradeWriter(MockChannelTradeMapper channelTradeMapper) {
        this.channelTradeMapper = channelTradeMapper;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MockChannelTrade recordSuccessfulTrade(String payNo,Long amountFen){
        LocalDateTime now = LocalDateTime.now().withNano(0);
        MockChannelTrade trade = new MockChannelTrade();
        trade.setPayNo(payNo);
        trade.setChannelTradeNo(
                UUID.randomUUID().toString().replace("-","")
        );
        trade.setPayAmountFen(amountFen);
        trade.setPaidTime(now);
        trade.setCreateTime(now);
        if (channelTradeMapper.insert(trade)!=1){
            throw new IllegalStateException(
                    "保存模拟渠道收款记录失败"
            );
        }
        return trade;
    }
}
