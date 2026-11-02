package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogLikeDeadLetterDetailDTO;
import com.m1kasa.content.api.BlogLikeDeadLetterPageDTO;
import org.springframework.amqp.core.Message;

public interface BlogLikeDeadLetterService {
    void save(Message message);
    BlogLikeDeadLetterPageDTO findPage(
            Integer status,
            int current,
            int size
    );

    BlogLikeDeadLetterDetailDTO findById(Long id);

    String replay(Long id);
}
