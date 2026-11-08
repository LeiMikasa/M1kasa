package com.m1kasa.m1kasaorderservice.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasaorderservice.entity.SeckillOrderOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillOrderOutboxMapper;
import com.m1kasa.voucher.api.SeckillStockDeductResult;
import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;
import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OrderStockResultService {

    private final SeckillOrderOutboxMapper outboxMapper;
    private final OrderStockConfirmService confirmService;
    private final OrderStockRejectService rejectService;

    public OrderStockResultService(
            SeckillOrderOutboxMapper outboxMapper,
            OrderStockConfirmService confirmService,
            OrderStockRejectService rejectService
    ) {
        this.outboxMapper = outboxMapper;
        this.confirmService = confirmService;
        this.rejectService = rejectService;
    }

    @Transactional
    public void complete(SeckillOrderOutbox task, SeckillStockDeductResult result) {
        if (result == null || result.outcome() == null) {
            throw new IllegalStateException("库存服务未返回有效结果");
        }

        int updated = outboxMapper.update(
                null,
                new LambdaUpdateWrapper<SeckillOrderOutbox>()
                        .eq(SeckillOrderOutbox::getId, task.getId())
                        .eq(SeckillOrderOutbox::getStatus, 0)
                        .set(SeckillOrderOutbox::getStatus, 1)
                        .set(SeckillOrderOutbox::getUpdateTime, LocalDateTime.now())
        );

        if (updated == 0) {
            SeckillOrderOutbox current = outboxMapper.selectById(task.getId());
            if (current != null && Integer.valueOf(1).equals(current.getStatus())) {
                return;
            }
            throw new IllegalStateException("扣库存任务状态异常，id=" + task.getId());
        }

        switch (result.outcome()) {
            case DEDUCTED -> {
                Long amountFen = result.payAmountFen();
                if (amountFen == null || amountFen <= 0) {
                    throw new IllegalStateException("库存扣减成功，但缺少有效的应付金额");
                }

                confirmService.confirm(
                        new SeckillStockDeductedMessage(
                                task.getRequestId(),
                                task.getOrderId(),
                                task.getVoucherId(),
                                task.getUserId()
                        ),
                        amountFen
                );
            }
            case REJECTED -> rejectService.reject(new SeckillStockRejectedMessage(
                    task.getRequestId(),
                    task.getOrderId(),
                    task.getVoucherId(),
                    task.getUserId(),
                    result.failureCode()
            ));
        }
    }
}
