package com.m1kasa.m1kasacontentservice.mapper;

import com.m1kasa.content.api.message.BlogCommentSearchMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BlogCommentSearchOutboxMapper {

    @Insert("""
            INSERT INTO tb_blog_comment_search_outbox
                (comment_id, blog_id, shop_id, content)
            SELECT c.id, c.blog_id, b.shop_id, c.content
            FROM tb_blog_comment AS c
            JOIN tb_blog AS b ON b.id = c.blog_id
            WHERE c.id = #{commentId}
              AND c.status = 1
              AND c.parent_id = 0
              AND b.status = 1
              AND b.shop_id > 0
            """)
    int enqueue(@Param("commentId") Long commentId);
    @Select("""
        SELECT comment_id, blog_id, shop_id, content
        FROM tb_blog_comment_search_outbox
        WHERE status = 0
        ORDER BY id
        LIMIT 20
        """)
    List<BlogCommentSearchMessage> findPending();

    @Update("""
        UPDATE tb_blog_comment_search_outbox
        SET status = 1
        WHERE comment_id = #{commentId}
          AND status = 0
        """)
    int markPublished(@Param("commentId") Long commentId);
}
