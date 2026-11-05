package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.m1kasavoucherservice.constant.SeckillStockResultStatus;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockDeduction;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockOutbox;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockDeductionMapper;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockOutboxMapper;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillVoucherMapper;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SeckillStockWriter {

    private final SeckillStockDeductionMapper deductionMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final SeckillStockOutboxMapper outboxMapper;

    public SeckillStockWriter(SeckillStockDeductionMapper deductionMapper, SeckillVoucherMapper seckillVoucherMapper, SeckillStockOutboxMapper outboxMapper) {
        this.deductionMapper = deductionMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.outboxMapper = outboxMapper;
    }
    @Transactional
    public void deduct(SeckillOrderCreatedMessage message){
        LocalDateTime now=LocalDateTime.now();
        //1.插入扣减记录，通过唯一约束拦截重复处理
        SeckillStockDeduction record=new SeckillStockDeduction();
        record.setOrderId(message.orderId());
        record.setRequestId(message.requestId());
        record.setUserId(message.userId());
        record.setVoucherId(message.voucherId());
        record.setCreateTime(now);
        record.setResultStatus(SeckillStockResultStatus.PROCESSING);
        if (deductionMapper.insert(record)!=1){
            throw new IllegalStateException("保存库存扣减记录失败");
        }

        //2.扣减数据库库存
        int affected=seckillVoucherMapper.deductStock(
                message.voucherId()
        );
        if (affected==1){
            record.setResultStatus(SeckillStockResultStatus.DEDUCTED);
        }else if (affected==0){
            //当前SQL没有匹配到可扣减库存
            //活动不存在，或者库存不大于0
            record.setResultStatus(SeckillStockResultStatus.REJECTED);
            record.setFailureCode("STOCK_UNAVAILABLE");
        }else {
            throw new IllegalStateException("库存扣减影响行数异常："+affected);
        }
        //3.保存本次请求的处理结果
        if (deductionMapper.updateById(record)!=1){
            throw new IllegalStateException("保存库存处理结果失败");
        }
        //4.保存待发送的结果通知
        SeckillStockOutbox outbox=new SeckillStockOutbox();
        outbox.setRequestId(message.requestId());
        outbox.setOrderId(message.orderId());
        outbox.setVoucherId(message.voucherId());
        outbox.setUserId(message.userId());

        outbox.setResultStatus(record.getResultStatus());
        outbox.setFailureCode(record.getFailureCode());
        outbox.setStatus(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("保存库存结果事件失败");
        }
    }
}
