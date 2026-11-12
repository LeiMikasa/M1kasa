package com.m1kasa.m1kasacontentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasacontentservice.entity.BlogComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogCommentMapper extends BaseMapper<BlogComment> {


    @Update("""
        UPDATE tb_blog_comment
        SET reply_count = reply_count + #{delta}
        WHERE id = #{rootId}
          AND blog_id = #{blogId}
          AND parent_id = 0
          AND reply_count + #{delta} >= 0
        """)
    int changeReplyCount(
            @Param("blogId") Long blogId,
            @Param("rootId") Long rootId,
            @Param("delta") int delta
    );

    @Update("""
        UPDATE tb_blog_comment
        SET status = 0
        WHERE id = #{commentId}
          AND blog_id = #{blogId}
          AND user_id = #{userId}
          AND status = 1
        """)
    int markDeleted(
            @Param("blogId") Long blogId,
            @Param("commentId") Long commentId,
            @Param("userId") Long userId
    );
}
