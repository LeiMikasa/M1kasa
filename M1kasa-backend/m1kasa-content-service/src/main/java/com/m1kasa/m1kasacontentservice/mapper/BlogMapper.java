package com.m1kasa.m1kasacontentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

     //查询笔记，同时锁住这条笔记记录
    //在事务中锁住这条笔记记录，同时操作同一篇笔记的点赞请求需要等待，事务提交或回滚后释放锁
    @Select(
            """
     SELECT id, status, liked
     FROM tb_blog
     WHERE id = #{id}
     FOR UPDATE



"""
    )
    Blog selectByIdForUpdate(@Param("id") Long id);


    //delta为1时加一，为-1时减一
    @Update("""
            UPDATE tb_blog
            SET liked = COALESCE(liked, 0) + #{delta}
            WHERE id = #{id}
              AND status = 1
              AND COALESCE(liked, 0) + #{delta} >= 0
            """)
    int changeLiked(
            @Param("id") Long id,
            @Param("delta") int delta
    );


}
