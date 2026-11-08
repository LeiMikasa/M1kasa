package com.m1kasa.m1kasapayservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasapayservice.constant.PaymentStatus;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import com.m1kasa.m1kasapayservice.mapper.PaymentOrderMapper;
import com.m1kasa.m1kasapayservice.service.PaymentOrderService;
import com.m1kasa.order.api.OrderPaymentInfoDTO;
import com.m1kasa.order.api.client.OrderPaymentClient;
import feign.FeignException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderPaymentClient orderPaymentClient;

    public PaymentOrderServiceImpl(PaymentOrderMapper paymentOrderMapper, OrderPaymentClient orderPaymentClient) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.orderPaymentClient = orderPaymentClient;
    }

    @Override
    public PaymentOrder create(Long orderId, Long userId) {
        if (orderId==null || orderId<=0 || userId==null || userId<=0){
            throw new IllegalArgumentException("订单ID或用户ID不合法");
        }
        //重复点击时，直接返回已经创建的支付单
        PaymentOrder existing = findOwnedPayment(orderId, userId);
        if (existing!=null){
            return existing;
        }
        //订单服务负责校验订单归属和待支付状态
        OrderPaymentInfoDTO order;
        try {
            order = orderPaymentClient.findPayable(orderId, userId);
        }catch (FeignException ex){
            //RPC期间，另一个请求可能已经创建了支付单
            existing=findOwnedPayment(orderId,userId);
            if (existing!=null){
                return existing;
            }
            throw ex;
        }
        if (order == null
                || !orderId.equals(order.orderId())
                || !userId.equals(order.userId())
                || order.payAmountFen() == null
                || order.payAmountFen() <= 0) {
            throw new IllegalStateException("订单服务未返回有效的支付信息");
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentOrder payment = new PaymentOrder();
        payment.setPayNo(UUID.randomUUID().toString().replace("-",""));
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setPayAmountFen(order.payAmountFen());
        payment.setChannel("MOCK");
        payment.setStatus(PaymentStatus.WAITING_PAYMENT);
        payment.setCreateTime(now);
        payment.setUpdateTime(now);
        try {
            if (paymentOrderMapper.insert(payment)!=1){
                throw new IllegalStateException("创建支付单失败");
            }
            return payment;
        }catch (DuplicateKeyException ex){
            //两个请求同时插入，由order_id唯一索引拦住第二个
            existing=findOwnedPayment(orderId,userId);
            if (existing!=null){
                return existing;
            }
            throw ex;
        }
    }

    private PaymentOrder findOwnedPayment(Long orderId,Long userId){
        PaymentOrder payment = paymentOrderMapper.selectOne(
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getOrderId, orderId)
        );
        if (payment!=null && !userId.equals(payment.getUserId())){
            throw new IllegalArgumentException("支付单不属于当前用户");
        }
        return payment;
    }
}
