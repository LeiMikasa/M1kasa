package com.m1kasa.m1kasapayservice.service;

import com.m1kasa.m1kasapayservice.client.MockPaymentCallbackClient;
import com.m1kasa.m1kasapayservice.entity.MockChannelTrade;
import com.m1kasa.m1kasapayservice.mapper.MockChannelTradeMapper;
import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import com.m1kasa.m1kasapayservice.support.MockPaymentSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Profile("local")
public class MockPaymentNotifier {
    private static final Logger log=
            LoggerFactory.getLogger(MockPaymentNotifier.class);
    private final MockChannelTradeMapper channelTradeMapper;
    private final MockPaymentSignature signature;
    private final MockPaymentCallbackClient callbackClient;
    private final Executor notifyExecutor;

    public MockPaymentNotifier(MockChannelTradeMapper channelTradeMapper,
                               MockPaymentSignature signature,
                               MockPaymentCallbackClient callbackClient,
                               @Qualifier("mockPaymentNotifyExecutor")Executor notifyExecutor) {
        this.channelTradeMapper = channelTradeMapper;
        this.signature = signature;
        this.callbackClient = callbackClient;
        this.notifyExecutor = notifyExecutor;
    }
    public void notifyPaidAsync(String payNo) {
        try {
            notifyExecutor.execute(() -> {
                // 在后台线程里执行 HTTP 回调
                notifyPaid(payNo);
            });
        } catch (RejectedExecutionException ex) {
            // 渠道收款已经提交，不能将任务提交失败当成收款失败
            log.warn(
                    "模拟渠道已收款，但回调任务未提交，payNo={}",
                    payNo,
                    ex
            );
        }
    }
    public boolean notifyPaid(String payNo){
        if (payNo==null||!payNo.matches("[0-9a-f]{32}")){
            throw new IllegalArgumentException("支付单号不合法");
        }
        try {
            //1.读取模拟渠道已经提交的成功收款记录
            MockChannelTrade trade = channelTradeMapper.selectById(payNo);
            if (trade==null){
                log.warn(
                        "模拟渠道没有成功收款记录,payNo={}",
                        payNo
                );
                return false;
            }
            //2.使用原交易的数据构造通知
            PaymentSuccessResult result=new PaymentSuccessResult(
                    trade.getPayNo(),
                    "MOCK",
                    trade.getChannelTradeNo(),
                    trade.getPayAmountFen(),
                    trade.getPaidTime()
            );
            //3.生成签名
            String sign=signature.sign(result);
            //4.发送HTTP回调，等待接收端响应
            String reply = callbackClient.notifyPaid(
                    result,
                    sign
            );
            //5.收到约定的success,才确认通知处理成功
            if ("success".equals(reply)){
                return true;
            }
            log.warn(
                    "模拟支付回调未返回success,payNo={}",
                    payNo
            );
            return false;

        }catch (RuntimeException ex){
            log.warn(
                    "模拟支付回调未获成功确认，payNo={}",
                    payNo,
                    ex
            );
            return false;
        }
    }

}
