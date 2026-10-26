package com.m1kasa.m1kasaorderservice.config;


import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*配置JSON转换和死信队列*/

@Configuration
public class RabbitMessageConfig {


    @Bean
    public MessageConverter rabbitMessageConverter(){
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables orderDeadLetterDeclarables() {
        DirectExchange exchange = new DirectExchange(
                "m1kasa.seckill.order.dlx",
                true,
                false
        );

        Queue queue = new Queue(
                "m1kasa.seckill.order.dlq",
                true,
                false,
                false
        );

        Binding binding = BindingBuilder
                .bind(queue)
                .to(exchange)
                .with("seckill.order.failed");

        return new Declarables(exchange, queue, binding);
    }



}
