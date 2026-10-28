package com.m1kasa.m1kasaorderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.m1kasaorderservice.entity.MqDeadLetter;
import com.m1kasa.m1kasaorderservice.mapper.MqDeadLetterMapper;
import com.m1kasa.m1kasaorderservice.mq.DeadLetterRecordFactory;
import com.m1kasa.m1kasaorderservice.mq.OrderDeadLetterReplayPublisher;
import com.m1kasa.m1kasaorderservice.service.MqDeadLetterService;
import com.m1kasa.order.api.DeadLetterDetailDTO;
import com.m1kasa.order.api.DeadLetterPageDTO;
import com.m1kasa.order.api.DeadLetterSummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;


@Service
public class MqDeadLetterServiceImpl implements MqDeadLetterService {

    private static final String SOURCE_QUEUE="m1kasa.seckill.order.queue";

    private final DeadLetterRecordFactory recordFactory;
    private final MqDeadLetterMapper deadLetterMapper;
    private static final Logger log =
            LoggerFactory.getLogger(MqDeadLetterServiceImpl.class);

    private final OrderDeadLetterReplayPublisher replayPublisher;
    public MqDeadLetterServiceImpl(DeadLetterRecordFactory recordFactory, MqDeadLetterMapper deadLetterMapper, OrderDeadLetterReplayPublisher replayPublisher) {
        this.recordFactory = recordFactory;
        this.deadLetterMapper = deadLetterMapper;
        this.replayPublisher = replayPublisher;
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

    @Override
    public String replay(Long id) {
       if (id==null||id<=0){
           throw new IllegalArgumentException("记录ID必须大于0");
       }
       MqDeadLetter record=deadLetterMapper.selectById(id);
       if (record==null){
           throw new NoSuchElementException("死信记录不存在");
       }
       if (!SOURCE_QUEUE.equals(record.getSourceQueue())){
           throw new IllegalStateException("该消息来源暂时不支持重发");
       }
       if (!Integer.valueOf(0).equals(record.getStatus())){
           throw new IllegalStateException("仅允许重发待处理记录");
       }
       //记住本次操作读到的采集版本
        Long version = record.getCaptureVersion();
       if (version==null||version<1){
           throw new IllegalStateException("采集版本不合法，请检查数据");
       }
       //发送前增加尝试次数。同时检查状态和版本
        int affected = deadLetterMapper.recordReplayAttempt(id, version);
        if (affected!=1){
            throw new IllegalStateException(
                    "记录状态或版本已变化，请刷新后重试"
            );
        }
        try {
            //等待发布确认以及退回检查
            replayPublisher.send(record).join();
        }catch (RuntimeException ex){
            Throwable cause=ex.getCause()==null?ex:ex.getCause();
            String error = cause.toString();
            //限制错误摘要长度
            if (error.length()>4000){
                error=error.substring(0,4000);
            }
            try {
                deadLetterMapper.recordReplayFailure(
                        id,
                        version,
                        error
                );
            }catch (DataAccessException updateEx){
                //即使错误记录保存失败，也保留两次异常供日志排查
                ex.addSuppressed(updateEx);
            }
            log.warn("死信重发未获发布确认，id={}",id,ex);
            throw new AmqpException(
                    "重发未获发布确认，消息可能已到达 MQ，请核实后再处理",
                    ex
            );
        }

        //能执行到这里说明发布检查已经完成
        try {
            int updated = deadLetterMapper.markReplayConfirmed(
                    id, version
            );
            if (updated==1){
                return "重发已获得发布确认，业务处理结果仍需核实";
            }
            return "重发已获得发布确认，但记录状态或版本已变化，请刷新查看";

        }catch (DataAccessException ex) {
            log.error("重发已获确认，但更新记录失败，id={}", id, ex);

            return "重发已获得发布确认，但数据库状态更新失败，请核实后再处理";
        }

    }
}
