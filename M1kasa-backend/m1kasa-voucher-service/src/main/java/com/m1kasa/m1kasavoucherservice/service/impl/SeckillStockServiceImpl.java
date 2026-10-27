package com.m1kasa.m1kasavoucherservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockDeduction;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockDeductionMapper;
import com.m1kasa.m1kasavoucherservice.service.SeckillStockService;
import com.m1kasa.m1kasavoucherservice.service.SeckillStockWriter;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SeckillStockServiceImpl implements SeckillStockService {

    private final SeckillStockWriter seckillStockWriter;
    private final SeckillStockDeductionMapper deductionMapper;

    public SeckillStockServiceImpl(SeckillStockWriter seckillStockWriter, SeckillStockDeductionMapper deductionMapper) {
        this.seckillStockWriter = seckillStockWriter;
        this.deductionMapper = deductionMapper;
    }

    @Override
    public void deduct(SeckillOrderCreatedMessage message) {
        validateMessage(message);
        try {
            seckillStockWriter.deduct(message);
        }catch (DuplicateKeyException ex){
            //内部事务已回滚，再判断是否确实处理过
            verifyAlreadyProcess(message,ex);
        }
    }

    private void validateMessage(
            SeckillOrderCreatedMessage message
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
            throw new IllegalArgumentException("订单创建事件参数不合法");
        }
    }
    private void verifyAlreadyProcess(
            SeckillOrderCreatedMessage message,
            DuplicateKeyException cause
    ){
        //先按订单ID查找已提交的扣减记录
        SeckillStockDeduction existing=
                deductionMapper.selectById(message.orderId());
        //也可能是request_id唯一约束发生冲突
        if (existing==null) {
            existing=deductionMapper.selectOne(
                    new LambdaQueryWrapper<SeckillStockDeduction>()
                            .eq(
                                    SeckillStockDeduction::getRequestId,
                                    message.requestId()
                            )
            );
        }
        if (existing==null){
            //无法证明已经处理成功，继续抛出异常
            throw cause;
        }
        boolean sameMessage =
                existing.getOrderId().equals(message.orderId())
                        && existing.getRequestId().equals(message.requestId())
                        && existing.getVoucherId().equals(message.voucherId())
                        && existing.getUserId().equals(message.userId());

        if (!sameMessage) {
            throw new IllegalStateException(
                    "库存扣减记录已存在，但消息内容不一致",
                    cause
            );
        }
        // 内容完全相同，说明之前已经扣减成功，正常结束即可
    }

}
