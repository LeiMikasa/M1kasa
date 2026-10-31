package com.m1kasa.m1kasacontentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasacontentservice.entity.BlogLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BlogLikeMapper extends BaseMapper<BlogLike> {


    // 在事务中读取并锁定当前用户的点赞关系
    @Select("""
            SELECT id,
                   blog_id,
                   user_id,
                   liked,
                   version,
                   create_time,
                   update_time
            FROM tb_blog_like
            WHERE blog_id = #{blogId}
              AND user_id = #{userId}
            FOR UPDATE
            """)
    BlogLike selectByBlogAndUserForUpdate(
       @Param("blogId") Long blogId,
       @Param("userId") Long userId
    );
}
