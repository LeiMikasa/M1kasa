package com.m1kasa.m1kasaorderservice.mq;


import com.m1kasa.m1kasaorderservice.service.OrderService;
import com.m1kasa.voucher.api.message.SeckillOrderCreateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderConsumer {
    private static final Logger log =
            LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final OrderService orderService;

    public SeckillOrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                        value = "m1kasa.seckill.order.queue",
                        durable = "true",
                        arguments = {
                                @Argument(
                                        name = "x-dead-letter-exchange",
                                        value = "m1kasa.seckill.order.dlx"
                                ),
                                @Argument(
                                        name = "x-dead-letter-routing-key",
                                        value = "seckill.order.failed"
                                )
                        }
                    ),
                    exchange = @Exchange(
                            value = "m1kasa.seckill.exchange",
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = "seckill.order.create"
            )
    )
    public void handle(SeckillOrderCreateMessage message){
        Long orderId=orderService.createSeckillOrder(message);
        log.info(
                "订单消息处理成功，requestId={}, orderId={}",
                message.requestId(),
                orderId
        );
    }
}
