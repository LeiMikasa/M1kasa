package com.m1kasa.m1kasacontentservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {
}
