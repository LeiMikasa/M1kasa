package com.m1kasa.m1kasauserservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasauserservice.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    @Select("""
 SELECT DISTINCT mine.follow_user_id
            FROM tb_follow mine
            INNER JOIN tb_follow other
                ON mine.follow_user_id = other.follow_user_id
            WHERE mine.user_id = #{userId}
              AND other.user_id = #{targetUserId}
            ORDER BY mine.follow_user_id ASC

""")
    List<Long> selectCommonFollowingIds(
      @Param("userId") Long userId,
      @Param("targetUserId") Long targetUserId
    );
}
