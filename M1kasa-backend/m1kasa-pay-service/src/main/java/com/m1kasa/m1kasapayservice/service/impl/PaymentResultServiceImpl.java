package com.m1kasa.m1kasapayservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasapayservice.constant.PaymentStatus;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import com.m1kasa.m1kasapayservice.entity.PaymentSuccessOutbox;
import com.m1kasa.m1kasapayservice.entity.PaymentTrade;
import com.m1kasa.m1kasapayservice.mapper.PaymentOrderMapper;
import com.m1kasa.m1kasapayservice.mapper.PaymentSuccessOutboxMapper;
import com.m1kasa.m1kasapayservice.mapper.PaymentTradeMapper;
import com.m1kasa.m1kasapayservice.mq.PaymentSuccessOutboxDispatcher;
import com.m1kasa.m1kasapayservice.service.PaymentResultService;
import com.m1kasa.m1kasapayservice.service.model.PaymentSuccessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class PaymentResultServiceImpl implements PaymentResultService {
    private static final Logger log=
            LoggerFactory.getLogger(PaymentResultServiceImpl.class);
    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentSuccessOutboxMapper outboxMapper;
    private final PaymentTradeMapper paymentTradeMapper;
    private final PaymentSuccessOutboxDispatcher outboxDispatcher;
    public PaymentResultServiceImpl(
            PaymentOrderMapper paymentOrderMapper,
            PaymentSuccessOutboxMapper outboxMapper,
            PaymentTradeMapper paymentTradeMapper,
            PaymentSuccessOutboxDispatcher outboxDispatcher
    ) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.outboxMapper = outboxMapper;
        this.paymentTradeMapper = paymentTradeMapper;
        this.outboxDispatcher = outboxDispatcher;
    }
    @Override
    @Transactional
    public void handleSuccess(PaymentSuccessResult result) {
        // 1. 检查成功结果的必要字段
        if (result == null
                || result.payNo() == null
                || result.payNo().isBlank()
                || result.channel() == null
                || result.channel().isBlank()
                || result.channelTradeNo() == null
                || result.channelTradeNo().isBlank()
                || result.payAmountFen() == null
                || result.payAmountFen() <= 0
                || result.paidTime() == null) {
            throw new IllegalArgumentException("支付成功结果不完整");
        }
        //2.锁住这笔支付单，串行处理它的成功结果
        PaymentOrder payment =
                paymentOrderMapper.selectByPayNoForUpdate(result.payNo());
        if (payment==null){
            throw new IllegalArgumentException("支付单不存在");
        }
        //3.校验渠道和金额，必须与原支付单一致
        if (!result.channel().equals(payment.getChannel())){
            throw new IllegalStateException("支付渠道不匹配");
        }
        if (!result.payAmountFen().equals(
                payment.getPayAmountFen()
        )) {
            throw new IllegalStateException("支付金额不匹配");
        }
        //4.已经处理成功：检查是否为同一笔渠道交易
        if (Objects.equals(
                payment.getStatus(),
                PaymentStatus.PAID
        )){
            if (!result.channelTradeNo().equals(
                    payment.getChannelTradeNo()
            )){
                throw new IllegalStateException(
                        "支付单已关联其他渠道交易"
                );
            }
            return;
        }
        if (!Objects.equals(
                payment.getStatus(),
                PaymentStatus.WAITING_PAYMENT
        )) {
            throw new IllegalStateException(
                    "当前支付单状态不允许推进为已支付"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        //5.将待支付状态推进已支付
        int updated = paymentOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getId, payment.getId())
                        .eq(
                                PaymentOrder::getStatus,
                                PaymentStatus.WAITING_PAYMENT
                        )
                        .set(
                                PaymentOrder::getStatus,
                                PaymentStatus.PAID
                        )
                        .set(
                                PaymentOrder::getChannelTradeNo,
                                result.channelTradeNo()
                        )
                        .set(
                                PaymentOrder::getPaidTime,
                                result.paidTime()
                        )
                        .set(PaymentOrder::getUpdateTime, now)
        );
        if (updated!=1){
            throw new IllegalStateException("更新支付单失败");
        }
        // 保存我方业务成功流水，使用渠道返回的原交易号
        PaymentTrade trade = new PaymentTrade();
        trade.setPayNo(payment.getPayNo());
        trade.setChannelTradeNo(result.channelTradeNo());
        trade.setPayAmountFen(payment.getPayAmountFen());
        trade.setPaidTime(result.paidTime());
        trade.setCreateTime(now);

        if (paymentTradeMapper.insert(trade) != 1) {
            throw new IllegalStateException("保存业务支付流水失败");
        }
        //6.保存支付成功事件
        PaymentSuccessOutbox outbox = new PaymentSuccessOutbox();
        outbox.setEventId(
                UUID.randomUUID().toString().replace("-","")
        );
        outbox.setPayNo(payment.getPayNo());
        outbox.setOrderId(payment.getOrderId());
        outbox.setUserId(payment.getUserId());
        outbox.setPayAmountFen(payment.getPayAmountFen());
        outbox.setChannel(payment.getChannel());
        outbox.setChannelTradeNo(result.channelTradeNo());
        outbox.setPaidTime(result.paidTime());
        outbox.setStatus(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);

        if (outboxMapper.insert(outbox) != 1
                || outbox.getId() == null) {
            throw new IllegalStateException(
                    "保存支付成功Outbox失败"
            );
        }

        Long outboxId = outbox.getId();

// 事务真正提交成功后，才尝试发送
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatchAfterCommit(outboxId);
                    }
                }
        );
    }
    private void dispatchAfterCommit(Long outboxId) {
        try {
            outboxDispatcher.dispatchOne(outboxId)
                    .whenComplete((unused, error) -> {
                        if (error != null) {
                            log.warn(
                                    "支付成功事件即时投递未完成，outboxId={}",
                                    outboxId,
                                    error
                            );
                        }
                    });
        } catch (RuntimeException ex) {
            // 支付事务已经提交，记录投递异常并保留待发送记录
            log.warn(
                    "提交支付成功事件投递任务失败，outboxId={}",
                    outboxId,
                    ex
            );
        }
    }
}
