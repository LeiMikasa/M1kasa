package com.m1kasa.m1kasaorderservice.service.impl;

import com.m1kasa.m1kasaorderservice.entity.MqDeadLetter;
import com.m1kasa.m1kasaorderservice.mapper.MqDeadLetterMapper;
import com.m1kasa.m1kasaorderservice.mq.DeadLetterRecordFactory;
import com.m1kasa.m1kasaorderservice.service.MqDeadLetterService;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class MqDeadLetterServiceImpl implements MqDeadLetterService {

    private static final String SOURCE_QUEUE="m1kasa.seckill.order.queue";

    private final DeadLetterRecordFactory recordFactory;
    private final MqDeadLetterMapper deadLetterMapper;

    public MqDeadLetterServiceImpl(DeadLetterRecordFactory recordFactory, MqDeadLetterMapper deadLetterMapper) {
        this.recordFactory = recordFactory;
        this.deadLetterMapper = deadLetterMapper;
    }

    @Override
    @Transactional
    public void saveOrderMessage(Message message) {
       //1.将原始消息转换成数据库记录
        MqDeadLetter record = recordFactory.from(
                SOURCE_QUEUE,
                message
        );

       //2.首次插入，重复收到则刷新为待处理
       deadLetterMapper.insertOrRefresh(record);
    }
}
