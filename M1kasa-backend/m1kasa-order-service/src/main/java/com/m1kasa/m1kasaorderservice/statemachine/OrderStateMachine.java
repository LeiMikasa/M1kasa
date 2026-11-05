package com.m1kasa.m1kasaorderservice.statemachine;

import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import org.springframework.stereotype.Component;

/**
 * 订单状态机
 */

@Component
public class OrderStateMachine {

    public Transition getTransition(OrderEvent event){
        if (event==null){
            throw new IllegalArgumentException("订单事件不能为空");
        }
        return switch (event){
            case STOCK_DEDUCTED -> new Transition(
                    OrderStatus.WAITING_STOCK,
                    OrderStatus.WAITING_PAYMENT
            );
            case STOCK_REJECTED -> new Transition(
              OrderStatus.WAITING_STOCK,
              OrderStatus.CANCELLED
            );
        };
    }

    /**
     * sourceStatus：允许转换的起始状态
     * targetStatus：转换后的目标状态
     */

    public record Transition(
      int sourceStatus,
      int targetStatus
    ){

    }
}
