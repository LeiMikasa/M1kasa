package com.m1kasa.m1kasacontentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasacontentservice.entity.BlogLikeDeadLetter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlogLikeDeadLetterMapper
        extends BaseMapper<BlogLikeDeadLetter> {

    @Insert("""
            INSERT INTO tb_blog_like_dead_letter (
                message_key,
                message_id,
                source_queue,
                body_base64,
                content_type,
                content_encoding,
                headers_json,
                death_reason,
                status,
                capture_version,
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
                1,
                0,
                NULL
            )
            ON DUPLICATE KEY UPDATE
                content_type = #{record.contentType},
                content_encoding = #{record.contentEncoding},
                headers_json = #{record.headersJson},
                death_reason = #{record.deathReason},
                status = 0,
                capture_version = capture_version + 1,
                last_error = NULL,
                update_time = CURRENT_TIMESTAMP
            """)
    int insertOrRefresh(
            @Param("record") BlogLikeDeadLetter record
    );
}
