package com.m1kasa.m1kasauserservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasauserservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("""
        SELECT id
        FROM tb_user
        WHERE id = #{userId}
        FOR UPDATE
        """)
    Long selectIdForUpdate(
            @Param("userId") Long userId
    );
}
