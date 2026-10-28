package com.m1kasa.m1kasaorderservice.service;

import com.m1kasa.order.api.DeadLetterDetailDTO;
import com.m1kasa.order.api.DeadLetterPageDTO;
import org.springframework.amqp.core.Message;

public interface MqDeadLetterService {

    void saveOrderMessage(Message message);

    DeadLetterPageDTO findPage(
      Integer status,
      int current,
      int size
    );
    DeadLetterDetailDTO findById(Long id);

    String replay(Long id);
}
