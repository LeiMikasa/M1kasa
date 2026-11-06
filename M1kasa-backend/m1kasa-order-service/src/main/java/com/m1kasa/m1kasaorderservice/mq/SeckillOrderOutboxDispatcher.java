package com.m1kasa.m1kasaorderservice.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m1kasa.m1kasaorderservice.entity.SeckillOrderOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillOrderOutboxMapper;
import com.m1kasa.m1kasaorderservice.service.OrderStockResultService;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import com.m1kasa.voucher.api.SeckillStockDeductRequest;
import com.m1kasa.voucher.api.SeckillStockDeductResult;
import com.m1kasa.voucher.api.client.VoucherStockClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Component
public class SeckillOrderOutboxDispatcher {
    private final Semaphore inFlight=new Semaphore(100);
    private final Executor executor;

    private final VoucherStockClient voucherStockClient;
    private final OrderStockResultService stockResultService;

    public SeckillOrderOutboxDispatcher(
            VoucherStockClient voucherStockClient,
            OrderStockResultService stockResultService,
            @Qualifier("orderOutboxExecutor") Executor executor
    ) {
        this.voucherStockClient = voucherStockClient;
        this.stockResultService = stockResultService;
        this.executor = executor;
    }
    public CompletableFuture<Void> dispatchOne(SeckillOrderOutbox record){
       if (!inFlight.tryAcquire()){
           return CompletableFuture.failedFuture(
                   new RejectedExecutionException("订单投递窗口已满")
           );
       }
       try {
           return CompletableFuture.runAsync(() -> {
               SeckillStockDeductRequest request = new SeckillStockDeductRequest(
                       record.getRequestId(),
                       record.getOrderId(),
                       record.getVoucherId(),
                       record.getUserId()
               );

               SeckillStockDeductResult result = voucherStockClient.deduct(request);
               stockResultService.complete(record, result);
           }, executor).whenComplete((unused, error) -> inFlight.release());
       }catch (RuntimeException ex){
           inFlight.release();;
           return CompletableFuture.failedFuture(ex);
       }
    }

}
