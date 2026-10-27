package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.m1kasavoucherservice.entity.SeckillStockDeduction;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockDeductionMapper;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillVoucherMapper;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SeckillStockWriter {

    private final SeckillStockDeductionMapper deductionMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;


    public SeckillStockWriter(SeckillStockDeductionMapper deductionMapper, SeckillVoucherMapper seckillVoucherMapper) {
        this.deductionMapper = deductionMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
    }
    @Transactional
    public void deduct(SeckillOrderCreatedMessage message){
        //1.插入扣减记录，通过唯一约束拦截重复处理
        SeckillStockDeduction record=new SeckillStockDeduction();
        record.setOrderId(message.orderId());
        record.setRequestId(message.requestId());
        record.setUserId(message.userId());
        record.setVoucherId(message.voucherId());
        record.setCreateTime(LocalDateTime.now());

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
    }
}
