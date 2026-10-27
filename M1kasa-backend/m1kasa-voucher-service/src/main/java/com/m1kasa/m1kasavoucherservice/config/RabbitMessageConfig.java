package com.m1kasa.m1kasavoucherservice.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.*;


/*
* JSON转换器  消息转换
* */
@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter rabbitMessageConverter(){
        return new JacksonJsonMessageConverter();
    }
    @Bean
    public Declarables stockDeadLetterDeclarables() {
        DirectExchange exchange = new DirectExchange(
                "m1kasa.voucher.stock.dlx",
                true,
                false
        );

        Queue queue = new Queue(
                "m1kasa.voucher.stock.dlq",
                true,
                false,
                false
        );

        Binding binding = BindingBuilder
                .bind(queue)
                .to(exchange)
                .with("seckill.stock.deduct.failed");

        return new Declarables(exchange, queue, binding);
    }

}
