package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.m1kasaorderservice.service.OrderPaymentService;
import com.m1kasa.pay.api.constant.PaymentMqConstants;
import com.m1kasa.pay.api.message.PaymentSuccessMessage;
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
public class OrderPaymentSuccessConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(
                    OrderPaymentSuccessConsumer.class
            );

    private final OrderPaymentService orderPaymentService;

    public OrderPaymentSuccessConsumer(
            OrderPaymentService orderPaymentService
    ) {
        this.orderPaymentService = orderPaymentService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = PaymentMqConstants.SUCCESS_QUEUE,
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = PaymentMqConstants.DEAD_LETTER_EXCHANGE
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = PaymentMqConstants.DEAD_LETTER_ROUTING_KEY
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = PaymentMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = PaymentMqConstants.SUCCESS_ROUTING_KEY
            )
    )
    public void handle(PaymentSuccessMessage message) {
        // 独立Service返回前完成数据库事务提交
        orderPaymentService.confirmPaid(message);

        log.info(
                "订单支付成功消息处理完成，eventId={}, orderId={}, payNo={}",
                message.eventId(),
                message.orderId(),
                message.payNo()
        );
    }
}
