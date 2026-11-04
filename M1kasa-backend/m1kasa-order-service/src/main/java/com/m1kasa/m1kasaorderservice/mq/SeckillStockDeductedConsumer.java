package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.m1kasaorderservice.service.OrderStockConfirmService;
import com.m1kasa.voucher.api.constant.VoucherMqConstants;
import com.m1kasa.voucher.api.message.SeckillStockDeductedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class SeckillStockDeductedConsumer {
    private static final Logger log=
            LoggerFactory.getLogger(SeckillStockDeductedConsumer.class);

    private final OrderStockConfirmService orderStockConfirmService;

    public SeckillStockDeductedConsumer(OrderStockConfirmService orderStockConfirmService) {
        this.orderStockConfirmService = orderStockConfirmService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = "m1kasa.order.stock.confirm.queue",
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = "m1kasa.order.stock.confirm.dlx"
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = "seckill.stock.confirm.failed"
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = VoucherMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = VoucherMqConstants.STOCK_DEDUCTED_ROUTING_KEY
            )
    )
    public void handle(SeckillStockDeductedMessage message){
        orderStockConfirmService.confirm(message);
        log.info(
                "库存成功回执处理完成，requestId={}, orderId={}",
                message.requestId(),
                message.orderId()
        );
    }
}
