package com.m1kasa.m1kasaorderservice.service;

import org.springframework.amqp.core.Message;

public interface MqDeadLetterService {

    void saveOrderMessage(Message message);
}
