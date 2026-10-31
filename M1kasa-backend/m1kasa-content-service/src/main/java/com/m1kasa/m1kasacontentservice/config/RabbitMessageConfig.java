package com.m1kasa.m1kasacontentservice.config;

import com.m1kasa.m1kasacontentservice.constant.BlogLikeMqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {
    @Bean
    public MessageConverter rabbitMessageConverter(){
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables blogLikeDeadLetterTopology() {
        // 死信交换机
        DirectExchange exchange = new DirectExchange(
                BlogLikeMqConstants.DEAD_LETTER_EXCHANGE,
                true,
                false
        );

        // 死信队列
        Queue queue = QueueBuilder
                .durable(BlogLikeMqConstants.DEAD_LETTER_QUEUE)
                .build();

        // 死信队列与交换机绑定
        Binding binding = BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(
                        BlogLikeMqConstants.DEAD_LETTER_ROUTING_KEY
                );

        return new Declarables(exchange, queue, binding);
    }
}
