package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.m1kasaorderservice.service.OrderStockRejectService;
import com.m1kasa.voucher.api.constant.VoucherMqConstants;
import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class SeckillStockRejectedConsumer {
    private static final Logger log
            = LoggerFactory.getLogger(SeckillStockRejectedConsumer.class);
    private final OrderStockRejectService stockRejectService;

    public SeckillStockRejectedConsumer(OrderStockRejectService stockRejectService) {
        this.stockRejectService = stockRejectService;
    }
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = "m1kasa.order.stock.reject.queue",
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = "m1kasa.order.stock.reject.dlx"
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = "seckill.stock.reject.failed"
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = VoucherMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = VoucherMqConstants.STOCK_REJECTED_ROUTING_KEY
            )
    )
    public void handle(SeckillStockRejectedMessage message) {
        stockRejectService.reject(message);
        log.info(
                "库存拒绝事件处理完成，requestId={}, orderId={}, failureCode={}",
                message.requestId(),
                message.orderId(),
                message.failureCode()
        );
    }
}
