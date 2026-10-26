package com.m1kasa.m1kasavoucherservice.config;


import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/*
* JSON转换器  消息转换
* */
@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter rabbitMessageConverter(){
        return new JacksonJsonMessageConverter();
    }


}
