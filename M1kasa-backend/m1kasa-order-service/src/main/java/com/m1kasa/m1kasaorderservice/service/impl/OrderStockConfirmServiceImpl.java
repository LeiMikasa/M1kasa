package com.m1kasa.m1kasaorderservice.service.impl;

import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderStockConfirmService;
import com.m1kasa.m1kasaorderservice.statemachine.OrderEvent;
import com.m1kasa.m1kasaorderservice.statemachine.OrderStateMachine;
import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderStockConfirmServiceImpl implements OrderStockConfirmService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final OrderStateMachine orderStateMachine;
    public OrderStockConfirmServiceImpl(VoucherOrderMapper voucherOrderMapper, OrderStateMachine orderStateMachine) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.orderStateMachine = orderStateMachine;
    }

    @Override
    public void confirm(SeckillStockDeductedMessage message,Long payAmountFen) {
          validateMessage(message);
          //根据库存成功事件取得状态转换规则
          OrderStateMachine.Transition transition=
                  orderStateMachine.getTransition(OrderEvent.STOCK_DEDUCTED);
          //1.尝试将库存确认中更新为待支付
        int affected = voucherOrderMapper.confirmStock(
                message.orderId(),
                message.requestId(),
                message.voucherId(),
                message.userId(),
                payAmountFen,
                transition.sourceStatus(),
                transition.targetStatus()
        );
        if (affected==1){
            return;
        }
        //2.没有更新到记录，查询原因
        VoucherOrder order = voucherOrderMapper.selectById(message.orderId());
        if (order==null){
            throw new IllegalStateException(
                    "库存成功消息对应的订单不存在"
            );
        }
        //3.必须先确认消息确实属于这笔订单
        boolean sameRequest =
                message.requestId().equals(order.getRequestId())
                        && message.voucherId().equals(order.getVoucherId())
                        && message.userId().equals(order.getUserId());
        if (!sameRequest) {
            throw new IllegalStateException(
                    "库存成功消息与订单信息不一致"
            );
        }
        Integer status = order.getStatus();
        //4.确认是，成功收到消息时正常结束
        if (Integer.valueOf(OrderStatus.WAITING_PAYMENT).equals(status)
        || Integer.valueOf(OrderStatus.PAID).equals(status)){
            return;
        }
        //5.已取消的订单不能被成功消息重新恢复
        if (Integer.valueOf(OrderStatus.CANCELLED).equals(status)){
            throw new IllegalStateException(
                    "已取消订单收到库存成功消息，需要核对和补偿"
            );
        }
        // 其他情况无法证明已处理成功，继续抛异常
        throw new IllegalStateException(
                "订单库存确认状态异常，status=" + status
        );
    }
    private void validateMessage(
        SeckillStockDeductedMessage message
    ){
        if (message==null
                || !StringUtils.hasText(message.requestId())
                || message.requestId().length()>64
                || message.orderId()==null
                || message.orderId()<=0
                || message.voucherId()==null
                || message.voucherId()<=0
                || message.userId()==null
                || message.userId()<=0){
            throw new IllegalArgumentException("库存扣减成功消息参数不合法");
        }
    }
}
