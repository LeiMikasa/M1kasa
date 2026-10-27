package com.m1kasa.m1kasaorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasaorderservice.entity.MqDeadLetter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MqDeadLetterMapper
        extends BaseMapper<MqDeadLetter> {

    @Insert("""
        INSERT INTO tb_mq_dead_letter (
                    message_key,
                    message_id,
                    source_queue,
                    body_base64,
                    content_type,
                    content_encoding,
                    headers_json,
                    death_reason,
                    status,
                    replay_count,
                    last_error
                )
                VALUES (
                    #{record.messageKey},
                    #{record.messageId},
                    #{record.sourceQueue},
                    #{record.bodyBase64},
                    #{record.contentType},
                    #{record.contentEncoding},
                    #{record.headersJson},
                    #{record.deathReason},
                    0,
                    0,
                    NULL
                )
                ON DUPLICATE KEY UPDATE
                    content_type = #{record.contentType},
                    content_encoding = #{record.contentEncoding},
                    headers_json = #{record.headersJson},
                    death_reason = #{record.deathReason},
                    status = 0,
                    update_time = CURRENT_TIMESTAMP


""")
    int insertOrRefresh(@Param("record") MqDeadLetter record);

}
