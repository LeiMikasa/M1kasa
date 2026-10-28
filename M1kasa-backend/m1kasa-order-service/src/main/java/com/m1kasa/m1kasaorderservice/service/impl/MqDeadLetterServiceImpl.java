package com.m1kasa.m1kasaorderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.m1kasaorderservice.entity.MqDeadLetter;
import com.m1kasa.m1kasaorderservice.mapper.MqDeadLetterMapper;
import com.m1kasa.m1kasaorderservice.mq.DeadLetterRecordFactory;
import com.m1kasa.m1kasaorderservice.service.MqDeadLetterService;
import com.m1kasa.order.api.DeadLetterDetailDTO;
import com.m1kasa.order.api.DeadLetterPageDTO;
import com.m1kasa.order.api.DeadLetterSummaryDTO;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;


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

    @Override
    public DeadLetterPageDTO findPage(Integer status, int current, int size) {
        if (status!=null&&(status<0||status>2)){
            throw new IllegalArgumentException(
                    "状态只能为0,1或2"
            );
        }
        if (current<1||size<1||size>50){
            throw new IllegalArgumentException(
              "页码必须大于0，每页条数必须在1到50之间"
            );
        }
        Page<MqDeadLetter> page = new Page<>(current, size);
        deadLetterMapper.selectPage(
                page,
                new LambdaQueryWrapper<MqDeadLetter>()
                //列表只查询摘要字段
                        .select(
                                MqDeadLetter::getId,
                                MqDeadLetter::getMessageId,
                                MqDeadLetter::getSourceQueue,
                                MqDeadLetter::getDeathReason,
                                MqDeadLetter::getStatus,
                                MqDeadLetter::getReplayCount,
                                MqDeadLetter::getLastError,
                                MqDeadLetter::getCreateTime,
                                MqDeadLetter::getUpdateTime
                        )
                //传入状态是才添加筛选条件
                        .eq(status!=null,
                                MqDeadLetter::getStatus,status)
                //最近更新的记录排在前面
                        .orderByDesc(
                                MqDeadLetter::getUpdateTime,
                                MqDeadLetter::getId
                        )
        );

        List<DeadLetterSummaryDTO> records = page.getRecords()
                .stream()
                .map(record -> new DeadLetterSummaryDTO(
                        record.getId().toString(),
                        record.getMessageId(),
                        record.getSourceQueue(),
                        record.getDeathReason(),
                        record.getStatus(),
                        record.getReplayCount(),
                        record.getLastError(),
                        record.getCreateTime(),
                        record.getUpdateTime()
                )).toList();

        return new DeadLetterPageDTO(
                records,page.getTotal(),current,size
        );
    }

    @Override
    public DeadLetterDetailDTO findById(Long id) {
        if (id==null||id<=0){
            throw new IllegalArgumentException("记录ID必须大于0");
        }
        MqDeadLetter record=deadLetterMapper.selectById(id);
        if (record==null){
            return null;
        }
        //将保存的Base64解码为字节，再按UTF-8显示正文预览
        String bodyText=new String(
                Base64.getDecoder().decode(record.getBodyBase64()),
                StandardCharsets.UTF_8
        );
        return new DeadLetterDetailDTO(
                record.getId().toString(),
                record.getMessageKey(),
                record.getMessageId(),
                record.getSourceQueue(),
                record.getContentType(),
                record.getContentEncoding(),
                record.getBodyBase64(),
                bodyText,
                record.getHeadersJson(),
                record.getDeathReason(),
                record.getStatus(),
                record.getReplayCount(),
                record.getLastError()
        );
    }
}
