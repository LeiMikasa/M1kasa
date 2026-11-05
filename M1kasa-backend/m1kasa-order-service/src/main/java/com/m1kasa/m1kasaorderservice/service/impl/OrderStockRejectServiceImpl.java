package com.m1kasa.m1kasaorderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasaorderservice.entity.SeckillStockCompensationOutbox;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.SeckillStockCompensationOutboxMapper;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderStockRejectService;
import com.m1kasa.m1kasaorderservice.statemachine.OrderEvent;
import com.m1kasa.m1kasaorderservice.statemachine.OrderStateMachine;
import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class OrderStockRejectServiceImpl implements OrderStockRejectService {

    private final VoucherOrderMapper voucherOrderMapper;
    private final OrderStateMachine orderStateMachine;
    private final SeckillStockCompensationOutboxMapper compensationOutboxMapper;

    public OrderStockRejectServiceImpl(
            VoucherOrderMapper voucherOrderMapper,
            OrderStateMachine orderStateMachine,
            SeckillStockCompensationOutboxMapper compensationOutboxMapper
    ) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.orderStateMachine = orderStateMachine;
        this.compensationOutboxMapper = compensationOutboxMapper;
    }

    @Override
    @Transactional
    public void reject(SeckillStockRejectedMessage message) {
        validateMessage(message);
        //1.取得库存拒绝事件对应的转换规则
        OrderStateMachine.Transition transition=
                orderStateMachine.getTransition(
                        OrderEvent.STOCK_REJECTED
                );
        //2.条件关闭订单，同时保存失败原因
        int affected = voucherOrderMapper.rejectStock(
                message.orderId(),
                message.requestId(),
                message.voucherId(),
                message.userId(),
                message.failureCode(),
                transition.sourceStatus(),
                transition.targetStatus()
        );
        if (affected==1){
            // 保存失败会使关单一起回滚，避免已关单却漏掉补偿通知。
            ensureCompensationOutbox(message);
            return;
        }
        //3.没有更新到订单，查询原因
        VoucherOrder order = voucherOrderMapper.selectById(message.orderId());
        if (order==null){
            throw new IllegalStateException("库存拒绝消息对应的订单不存在");
        }
        //4.核对消息是否属于这笔订单
        boolean sameRequest=message.requestId().equals(order.getRequestId())
                && message.voucherId().equals(order.getVoucherId())
                && message.userId().equals(order.getUserId());

        if (!sameRequest){
            throw new IllegalStateException("库存拒绝消息与订单信息不一致");
        }
        //5.只有已经处于目标状态，才可能是重复消息
        if (!Integer.valueOf(transition.targetStatus())
                .equals(order.getStatus())){
            throw new IllegalStateException(
                    "订单状态与库存拒绝事件冲突，status="
                            + order.getStatus()
            );
        }
        // 6.已取消，也必须核对关闭原因
        if (!message.failureCode().equals(order.getFailureCode())) {
            throw new IllegalStateException(
                    "订单已取消，但关闭原因与库存拒绝事件不一致"
            );
        }

        // 同一请求、相同关闭状态、相同关闭原因：核对已有通知，必要时补建。
        ensureCompensationOutbox(message);
    }

    /**
     * 与关单共用 reject() 的事务。重复消息不重建或重置已发送通知；
     * 对历史上已按相同原因关闭、但尚无通知的订单，补建一条待发送记录。
     */
    private void ensureCompensationOutbox(SeckillStockRejectedMessage message) {
        SeckillStockCompensationOutbox existing =
                compensationOutboxMapper.selectOne(
                        new LambdaQueryWrapper<SeckillStockCompensationOutbox>()
                                .eq(
                                        SeckillStockCompensationOutbox::getOrderId,
                                        message.orderId()
                                )
                );

        if (existing != null) {
            boolean sameEvent =
                    message.requestId().equals(existing.getRequestId())
                            && message.orderId().equals(existing.getOrderId())
                            && message.voucherId().equals(existing.getVoucherId())
                            && message.userId().equals(existing.getUserId())
                            && message.failureCode().equals(existing.getFailureCode());

            if (!sameEvent) {
                throw new IllegalStateException("库存补偿通知已存在，但业务信息不一致");
            }

            if (!Integer.valueOf(0).equals(existing.getStatus())
                    && !Integer.valueOf(1).equals(existing.getStatus())) {
                throw new IllegalStateException("库存补偿通知发送状态不合法");
            }
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillStockCompensationOutbox record =
                new SeckillStockCompensationOutbox();
        record.setRequestId(message.requestId());
        record.setOrderId(message.orderId());
        record.setVoucherId(message.voucherId());
        record.setUserId(message.userId());
        record.setFailureCode(message.failureCode());
        record.setStatus(0);
        record.setCreateTime(now);
        record.setUpdateTime(now);

        if (compensationOutboxMapper.insert(record) != 1) {
            throw new IllegalStateException("保存库存补偿通知失败");
        }
    }

    private void validateMessage(
            SeckillStockRejectedMessage message
    ) {
        if (message == null
                || !StringUtils.hasText(message.requestId())
                || message.requestId().length() > 64
                || message.orderId() == null
                || message.orderId() <= 0
                || message.voucherId() == null
                || message.voucherId() <= 0
                || message.userId() == null
                || message.userId() <= 0) {
            throw new IllegalArgumentException(
                    "库存拒绝消息参数不合法"
            );
        }

        // 当前只处理已经定义的库存不可用原因
        if (!"STOCK_UNAVAILABLE".equals(message.failureCode())) {
            throw new IllegalArgumentException(
                    "不支持的库存拒绝原因："
                            + message.failureCode()
            );
        }
    }
}
