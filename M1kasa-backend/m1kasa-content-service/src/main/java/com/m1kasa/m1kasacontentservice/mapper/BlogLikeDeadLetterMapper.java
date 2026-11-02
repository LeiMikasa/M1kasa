package com.m1kasa.m1kasacontentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasacontentservice.entity.BlogLikeDeadLetter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
    //发送前  记录重发尝试
    @Update("""
        UPDATE tb_blog_like_dead_letter
        SET replay_count = replay_count + 1,
            update_time = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND capture_version = #{captureVersion}
          AND status = 0
        """)
    int recordReplayAttempt(
            @Param("id") Long id,
            @Param("captureVersion") Long captureVersion
    );
    //发布确认成功后，标记重发已确认
    @Update("""
        UPDATE tb_blog_like_dead_letter
        SET status = 1,
            last_error = NULL,
            update_time = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND capture_version = #{captureVersion}
          AND status = 0
        """)
    int markReplayConfirmed(
            @Param("id") Long id,
            @Param("captureVersion") Long captureVersion
    );
    //发送失败或确认超时，记录错误
    @Update("""
        UPDATE tb_blog_like_dead_letter
        SET last_error = #{error},
            update_time = CURRENT_TIMESTAMP
        WHERE id = #{id}
          AND capture_version = #{captureVersion}
          AND status = 0
        """)
    int recordReplayFailure(
            @Param("id") Long id,
            @Param("captureVersion") Long captureVersion,
            @Param("error") String error
    );
}
