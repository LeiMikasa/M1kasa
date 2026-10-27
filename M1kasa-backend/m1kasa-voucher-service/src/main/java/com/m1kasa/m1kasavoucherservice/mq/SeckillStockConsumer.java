package com.m1kasa.m1kasavoucherservice.mq;

import com.m1kasa.m1kasavoucherservice.service.SeckillStockService;
import com.m1kasa.order.api.constant.OrderMqConstants;
import com.m1kasa.order.api.message.SeckillOrderCreatedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class SeckillStockConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(SeckillStockConsumer.class);

    private final SeckillStockService stockService;

    public SeckillStockConsumer(SeckillStockService stockService) {
        this.stockService = stockService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = "m1kasa.voucher.stock.deduct.queue",
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = "m1kasa.voucher.stock.dlx"
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = "seckill.stock.deduct.failed"
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = OrderMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = OrderMqConstants.SECKILL_ORDER_CREATED_ROUTING_KEY
            )
    )
    public void handle(SeckillOrderCreatedMessage message){
        stockService.deduct(message);
        log.info(
                "库存消息处理成功，orderId={},voucherId={}",
                message.orderId(),
                message.voucherId()
        );
    }
}
