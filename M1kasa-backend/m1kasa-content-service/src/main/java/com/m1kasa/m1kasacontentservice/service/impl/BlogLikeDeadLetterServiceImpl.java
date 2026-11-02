package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.content.api.BlogLikeDeadLetterDetailDTO;
import com.m1kasa.content.api.BlogLikeDeadLetterPageDTO;
import com.m1kasa.content.api.BlogLikeDeadLetterSummaryDTO;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeMqConstants;
import com.m1kasa.m1kasacontentservice.entity.BlogLikeDeadLetter;
import com.m1kasa.m1kasacontentservice.mapper.BlogLikeDeadLetterMapper;
import com.m1kasa.m1kasacontentservice.mq.BlogLikeDeadLetterRecordFactory;
import com.m1kasa.m1kasacontentservice.mq.BlogLikeDeadLetterReplayPublisher;
import com.m1kasa.m1kasacontentservice.service.BlogLikeDeadLetterService;
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
public class BlogLikeDeadLetterServiceImpl implements BlogLikeDeadLetterService {
    private final BlogLikeDeadLetterRecordFactory recordFactory;
    private final BlogLikeDeadLetterMapper deadLetterMapper;
    private static final Logger log= LoggerFactory.getLogger(BlogLikeDeadLetterServiceImpl.class);
    private final BlogLikeDeadLetterReplayPublisher replayPublisher;
    public BlogLikeDeadLetterServiceImpl(BlogLikeDeadLetterRecordFactory recordFactory, BlogLikeDeadLetterMapper deadLetterMapper, BlogLikeDeadLetterReplayPublisher replayPublisher) {
        this.recordFactory = recordFactory;
        this.deadLetterMapper = deadLetterMapper;
        this.replayPublisher = replayPublisher;
    }

    @Override
    @Transactional
    public void save(Message message) {
        //1.将原始消息转换为数据库实体
        BlogLikeDeadLetter record = recordFactory.from(
                BlogLikeMqConstants.QUEUE,
                message
        );
        //2.首次插入，重复采集则刷新原纪录
        int affected = deadLetterMapper.insertOrRefresh(record);
        if (affected<=0){
            throw new IllegalStateException("保存点赞死信记录失败");
        }
    }

    @Override
    public BlogLikeDeadLetterPageDTO findPage(
            Integer status,
            int current,
            int size
    ) {
        // status为空，表示不按状态筛选
        if (status != null && (status < 0 || status > 2)) {
            throw new IllegalArgumentException(
                    "状态只能为0、1或2"
            );
        }

        if (current < 1 || size < 1 || size > 50) {
            throw new IllegalArgumentException(
                    "页码必须大于0，每页条数必须在1到50之间"
            );
        }

        Page<BlogLikeDeadLetter> page =
                new Page<>(current, size);

        deadLetterMapper.selectPage(
                page,
                new LambdaQueryWrapper<BlogLikeDeadLetter>()
                        // 列表只查询摘要字段
                        .select(
                                BlogLikeDeadLetter::getId,
                                BlogLikeDeadLetter::getMessageId,
                                BlogLikeDeadLetter::getSourceQueue,
                                BlogLikeDeadLetter::getDeathReason,
                                BlogLikeDeadLetter::getStatus,
                                BlogLikeDeadLetter::getCaptureVersion,
                                BlogLikeDeadLetter::getReplayCount,
                                BlogLikeDeadLetter::getLastError,
                                BlogLikeDeadLetter::getCreateTime,
                                BlogLikeDeadLetter::getUpdateTime
                        )
                        .eq(
                                status != null,
                                BlogLikeDeadLetter::getStatus,
                                status
                        )
                        .orderByDesc(
                                BlogLikeDeadLetter::getUpdateTime,
                                BlogLikeDeadLetter::getId
                        )
        );

        List<BlogLikeDeadLetterSummaryDTO> records =
                page.getRecords()
                        .stream()
                        .map(record ->
                                new BlogLikeDeadLetterSummaryDTO(
                                        record.getId().toString(),
                                        record.getMessageId(),
                                        record.getSourceQueue(),
                                        record.getDeathReason(),
                                        record.getStatus(),
                                        record.getCaptureVersion(),
                                        record.getReplayCount(),
                                        record.getLastError(),
                                        record.getCreateTime(),
                                        record.getUpdateTime()
                                )
                        )
                        .toList();

        return new BlogLikeDeadLetterPageDTO(
                records,
                page.getTotal(),
                current,
                size
        );
    }

    @Override
    public BlogLikeDeadLetterDetailDTO findById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "记录ID必须大于0"
            );
        }

        BlogLikeDeadLetter record =
                deadLetterMapper.selectById(id);

        // 交给Controller转换为404响应
        if (record == null) {
            return null;
        }

        if (record.getBodyBase64() == null) {
            throw new IllegalStateException(
                    "死信记录缺少原始正文"
            );
        }

        String bodyText;

        try {
            // 仅做UTF-8文本预览，不要求正文是合法JSON
            byte[] body = Base64.getDecoder()
                    .decode(record.getBodyBase64());

            bodyText = new String(
                    body,
                    StandardCharsets.UTF_8
            );

        } catch (IllegalArgumentException ex) {
            // 存储数据异常，不应当作请求参数错误
            throw new IllegalStateException(
                    "死信记录正文无法解码",
                    ex
            );
        }

        return new BlogLikeDeadLetterDetailDTO(
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
                record.getCaptureVersion(),
                record.getReplayCount(),
                record.getLastError(),
                record.getCreateTime(),
                record.getUpdateTime()
        );
    }


    @Override
    public String replay(Long id) {
        // 1. 校验记录ID
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "记录ID必须大于0"
            );
        }

        // 2. 查询数据库中的死信记录
        BlogLikeDeadLetter record =
                deadLetterMapper.selectById(id);

        if (record == null) {
            throw new NoSuchElementException(
                    "死信记录不存在"
            );
        }

        // 3. 校验消息来源和处理状态
        if (!BlogLikeMqConstants.QUEUE.equals(
                record.getSourceQueue()
        )) {
            throw new IllegalStateException(
                    "该消息来源暂不支持重发"
            );
        }

        if (!Integer.valueOf(0).equals(record.getStatus())) {
            throw new IllegalStateException(
                    "仅允许重发待处理记录"
            );
        }

        // 4. 记住本次读取到的采集版本
        Long captureVersion = record.getCaptureVersion();

        if (captureVersion == null || captureVersion < 1) {
            throw new IllegalStateException(
                    "采集版本不合法，请检查数据"
            );
        }

        // 5. 记录尝试次数，同时检查版本和状态
        int affected = deadLetterMapper.recordReplayAttempt(
                id,
                captureVersion
        );

        if (affected != 1) {
            throw new IllegalStateException(
                    "记录状态或版本已变化，请刷新后重试"
            );
        }

        // 6. 发送消息，等待发布确认和退回检查
        try {
            replayPublisher.send(record).join();

        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause() == null
                    ? ex
                    : ex.getCause();

            String error = cause.toString();

            // 对应表中的 last_error VARCHAR(2000)
            if (error.length() > 2000) {
                error = error.substring(0, 2000);
            }

            try {
                deadLetterMapper.recordReplayFailure(
                        id,
                        captureVersion,
                        error
                );

            } catch (DataAccessException updateEx) {
                // 保留消息发布异常和错误记录保存异常
                ex.addSuppressed(updateEx);
            }

            log.warn(
                    "点赞死信重发未获确认，id={}, captureVersion={}",
                    id,
                    captureVersion,
                    ex
            );

            throw new AmqpException(
                    "重发未获发布确认，消息可能已到达MQ，请核实后再处理",
                    ex
            );
        }

        // 7. 发布检查成功后，尝试更新数据库状态
        try {
            int updated = deadLetterMapper.markReplayConfirmed(
                    id,
                    captureVersion
            );

            if (updated == 1) {
                return "重发已获得发布确认，业务处理结果仍需核实";
            }

            // 期间可能已经再次进入死信并被采集，保留新状态
            return "重发已获得发布确认，但记录状态或版本已变化，请刷新查看";

        } catch (DataAccessException ex) {
            // MQ发布成功与数据库状态更新失败，要分别说明
            log.error(
                    "点赞死信重发已获确认，但更新记录失败，id={}",
                    id,
                    ex
            );

            return "重发已获得发布确认，但数据库状态更新失败，请核实后再处理";
        }
    }
}
