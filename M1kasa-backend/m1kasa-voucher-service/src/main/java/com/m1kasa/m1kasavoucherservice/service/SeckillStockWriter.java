package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.m1kasavoucherservice.constant.SeckillStockResultStatus;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockDeduction;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockOutbox;
import com.m1kasa.m1kasavoucherservice.entity.Voucher;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockDeductionMapper;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockOutboxMapper;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillVoucherMapper;
import com.m1kasa.m1kasavoucherservice.mapper.VoucherMapper;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.SeckillStockDeductRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SeckillStockWriter {

    private final SeckillStockDeductionMapper deductionMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final SeckillStockOutboxMapper outboxMapper;
    private final VoucherMapper voucherMapper;
    public SeckillStockWriter(SeckillStockDeductionMapper deductionMapper, SeckillVoucherMapper seckillVoucherMapper, SeckillStockOutboxMapper outboxMapper, VoucherMapper voucherMapper) {
        this.deductionMapper = deductionMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.outboxMapper = outboxMapper;
        this.voucherMapper = voucherMapper;
    }
    // 原来的 MQ 消费者仍然走这个方法
    @Transactional
    public void deduct(SeckillStockDeductRequest request) {

        SeckillStockDeduction record = writeDeduction(request);

        // MQ 模式通过 Outbox 把结果通知订单服务
        SeckillStockOutbox outbox = new SeckillStockOutbox();
        outbox.setRequestId(record.getRequestId());
        outbox.setOrderId(record.getOrderId());
        outbox.setVoucherId(record.getVoucherId());
        outbox.setUserId(record.getUserId());
        outbox.setResultStatus(record.getResultStatus());
        outbox.setFailureCode(record.getFailureCode());
        outbox.setStatus(0);
        outbox.setCreateTime(record.getCreateTime());
        outbox.setUpdateTime(record.getCreateTime());

        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("保存库存结果事件失败");
        }
    }

    // 后续 RPC 接口通过 Service 调用这个方法
    @Transactional
    public SeckillStockDeduction deductForRpc(SeckillStockDeductRequest request) {
        return writeDeduction(request);
    }

    // 两个入口共用的扣减逻辑
    private SeckillStockDeduction writeDeduction(SeckillStockDeductRequest request) {
        SeckillStockDeduction record = new SeckillStockDeduction();
        record.setOrderId(request.orderId());
        record.setRequestId(request.requestId());
        record.setVoucherId(request.voucherId());
        record.setUserId(request.userId());
        record.setCreateTime(LocalDateTime.now());
        record.setResultStatus(SeckillStockResultStatus.PROCESSING);
        // TODO 幂等
        // 先插流水，唯一约束会拦住同一请求的重复扣减
        if (deductionMapper.insert(record) != 1) {
            throw new IllegalStateException("保存库存扣减记录失败");
        }

        int affected = seckillVoucherMapper.deductStock(request.voucherId());

        if (affected == 1) {
            Voucher voucher = voucherMapper.selectById(request.voucherId());
            Long payAmountFen = voucher == null ? null : voucher.getPayValue();

            if (payAmountFen == null || payAmountFen <= 0) {
                throw new IllegalStateException("优惠券应付金额不合法");
            }

            record.setPayAmountFen(payAmountFen);
            record.setResultStatus(SeckillStockResultStatus.DEDUCTED);
        } else if (affected == 0) {
            record.setResultStatus(SeckillStockResultStatus.REJECTED);
            record.setFailureCode("STOCK_UNAVAILABLE");
        } else {
            throw new IllegalStateException("库存扣减影响行数异常：" + affected);
        }

        if (deductionMapper.updateById(record) != 1) {
            throw new IllegalStateException("保存库存处理结果失败");
        }

        return record;
    }
}
