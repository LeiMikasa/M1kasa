package com.m1kasa.m1kasaorderservice.service.impl;

import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderPaymentService;
import com.m1kasa.m1kasaorderservice.statemachine.OrderEvent;
import com.m1kasa.m1kasaorderservice.statemachine.OrderStateMachine;
import com.m1kasa.pay.api.message.PaymentSuccessMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final VoucherOrderMapper orderMapper;
    private final OrderStateMachine stateMachine;

    public OrderPaymentServiceImpl(VoucherOrderMapper orderMapper, OrderStateMachine stateMachine) {
        this.orderMapper = orderMapper;
        this.stateMachine = stateMachine;
    }


    @Override
    @Transactional
    public void confirmPaid(PaymentSuccessMessage message) {
        validateMessage(message);
        //1.锁住当前订单，串行处理它的支付成功消息
        VoucherOrder order = orderMapper.selectByIdForUpdate(
                message.orderId()
        );
        if (order==null){
            throw new IllegalStateException("支付成功消息对应的订单不存在");
        }
        //2.校验订单归属和金额快照
        if (!message.userId().equals(order.getUserId())){
            throw new IllegalStateException(
                    "支付消息与订单用户不一致"
            );
        }
        if (!message.payAmountFen().equals(
                order.getPayAmountFen()
        )) {
            throw new IllegalStateException(
                    "支付消息与订单金额不一致"
            );
        }
        //3.已支付时，必须确认是同一笔支付的重复信息
        if (Objects.equals(order.getStatus(), OrderStatus.PAID)){
            boolean samePayment=
                    message.payNo().equals(order.getPayNo())
                    &&message.channel().equals(
                            order.getPayChannel()
                    )
                    && message.channelTradeNo().equals(
                            order.getChannelTradeNo()
                    );
            if (!samePayment) {
                throw new IllegalStateException(
                        "已支付订单收到不同支付交易"
                );
            }

            return;
        }
        //4.通过状态机取得运行的状态转换
        OrderStateMachine.Transition transition = stateMachine.getTransition(
                OrderEvent.PAYMENT_SUCCEEDED
        );
        if (!Objects.equals(
                order.getStatus(),
                transition.sourceStatus()
        )){
            throw new IllegalStateException(
                    "当前订单状态不允许确认支付,status="+order.getStatus()
            );
        }
        //5.保存支付身份和渠道成功时间
        order.setStatus(transition.targetStatus());
        order.setPayNo(message.payNo());
        order.setPayChannel(message.channel());
        order.setChannelTradeNo(message.channelTradeNo());
        order.setPaidTime(message.paidTime());
        order.setUpdateTime(LocalDateTime.now());

        if (orderMapper.updateById(order) != 1) {
            throw new IllegalStateException(
                    "更新订单支付状态失败"
            );
        }

    }

    public void validateMessage(PaymentSuccessMessage message){
        if (message == null
                || !validText(message.eventId(), 32)
                || !validText(message.payNo(), 32)
                || message.orderId() == null
                || message.orderId() <= 0
                || message.userId() == null
                || message.userId() <= 0
                || message.payAmountFen() == null
                || message.payAmountFen() <= 0
                || !validText(message.channel(), 16)
                || !validText(message.channelTradeNo(), 64)
                || message.paidTime() == null) {
            throw new IllegalArgumentException(
                    "支付成功消息参数不合法"
            );
        }
    }
    private boolean validText(String value, int maxLength) {
        return value != null
                && !value.isBlank()
                && value.length() <= maxLength;
    }
}
