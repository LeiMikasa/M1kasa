package com.m1kasa.m1kasasearchservice.config;

import com.m1kasa.shop.api.constant.ShopSearchMqConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {
    @Bean
    public MessageConverter rabbitMessageConverter(){
        //将JSON消息转换为java对象
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public MessageRecoverer shopSearchMessageRecover(){
        //重试耗尽后拒绝消息，不重新入原队列
        return new RejectAndDontRequeueRecoverer();
    }
    @Bean
    public Declarables shopSearchDeadLetterTopology(){
        DirectExchange exchange=new DirectExchange(
                ShopSearchMqConstants.DEAD_LETTER_EXCHANGE,
                true,
                false
        );
        Queue queue = new Queue(
                ShopSearchMqConstants.DEAD_LETTER_QUEUE,
                true,
                false,
                false
        );

        Binding binding = BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(ShopSearchMqConstants.DEAD_LETTER_ROUTING_KEY);

        return new Declarables(exchange, queue, binding);
    }
}
