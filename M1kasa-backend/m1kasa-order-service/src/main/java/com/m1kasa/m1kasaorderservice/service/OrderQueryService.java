package com.m1kasa.m1kasaorderservice.service;


import com.m1kasa.order.api.OrderPageDTO;
import com.m1kasa.order.api.OrderSummaryDTO;

public interface OrderQueryService {

    OrderSummaryDTO findByVoucher(Long voucherId, Long userId);

    OrderPageDTO findMyOrders(Long userId,int current,int size);

    OrderSummaryDTO findById(Long orderId,Long userId);
}
