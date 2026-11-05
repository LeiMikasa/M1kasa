package com.m1kasa.m1kasavoucherservice.mq;

import com.m1kasa.m1kasavoucherservice.service.SeckillStockCompensationService;
import com.m1kasa.order.api.constant.OrderMqConstants;
import com.m1kasa.order.api.message.SeckillOrderClosedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SeckillStockCompensationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    SeckillStockCompensationConsumer.class
            );

    private final SeckillStockCompensationService compensationService;

    public SeckillStockCompensationConsumer(
            SeckillStockCompensationService compensationService
    ) {
        this.compensationService = compensationService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = "m1kasa.voucher.stock.compensate.queue",
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = "m1kasa.voucher.stock.compensate.dlx"
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = "seckill.stock.compensate.failed"
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = OrderMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = OrderMqConstants.SECKILL_ORDER_CLOSED_ROUTING_KEY
            )
    )
    public void handle(SeckillOrderClosedMessage message) {
        // Service 完成校验和 Redis 补偿后，才会正常返回
        compensationService.compensate(message);

        log.info(
                "库存补偿消息处理完成，requestId={}, orderId={}, voucherId={}",
                message.requestId(),
                message.orderId(),
                message.voucherId()
        );
    }
}
