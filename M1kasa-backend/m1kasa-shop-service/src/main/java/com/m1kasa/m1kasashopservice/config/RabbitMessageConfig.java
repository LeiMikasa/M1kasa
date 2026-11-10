package com.m1kasa.m1kasashopservice.config;

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

}
