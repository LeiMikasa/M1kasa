package com.m1kasa.m1kasavoucherservice.service;

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

        if (deductionMapper.insert(record)!=1){
            throw new IllegalStateException("保存库存扣减记录失败");
        }

        //2.扣减数据库库存
        int affected=seckillVoucherMapper.deductStock(
                message.voucherId()
        );
        if (affected!=1){
            // 抛异常，让刚才插入的扣减记录一起回滚
            throw new IllegalStateException(
                    "数据库库存扣减失败，活动不存在或库存不足"
            );
        }
        //3.保存待发送的"库存扣减成功"事件
        SeckillStockOutbox outbox=new SeckillStockOutbox();
        outbox.setRequestId(message.requestId());
        outbox.setOrderId(message.orderId());
        outbox.setVoucherId(message.voucherId());
        outbox.setUserId(message.userId());
        //等待后台任务发送
        outbox.setStatus(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        if (outboxMapper.insert(outbox)!=1){
            throw new IllegalStateException("保存库存成功事件-失败");
        }
    }
}
