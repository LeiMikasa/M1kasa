package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.BlogDetailDTO;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogQueryService;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.client.UserClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BlogQueryServiceImpl implements BlogQueryService {

    private static final int PUBLISHED=1;

    private final BlogMapper blogMapper;
    private final UserClient userClient;

    public BlogQueryServiceImpl(BlogMapper blogMapper, UserClient userClient) {
        this.blogMapper = blogMapper;
        this.userClient = userClient;
    }

    @Override
    public BlogDetailDTO findById(Long id, String authorization) {
          //只查询已发布的笔记
        Blog blog = blogMapper.selectOne(
                new LambdaQueryWrapper<Blog>()
                        .eq(Blog::getId, id)
                        .eq(Blog::getStatus, PUBLISHED)
        );

        if (blog==null){
            return null;
        }
        //通过feign查询作者资料
        Result<List<UserDTO>> reply = userClient.listByIds(
                List.of(blog.getUserId()),
                authorization
        );
        //调用结果异常时，交给controller返回错误
        if (reply==null || !Boolean.TRUE.equals(reply.success()) || reply.data()==null){
            throw new IllegalStateException("用户服务返回异常结果");
        }

        //按用户ID匹配
        UserDTO author = reply.data().stream().
                filter(user -> user != null && blog.getUserId().equals(user.id()))
                .findFirst()
                .orElse(null);

        return new BlogDetailDTO(
                blog.getId(),
                blog.getShopId(),
                blog.getUserId(),
                author==null?"未知用户":author.nickName(),
                author==null?null:author.icon(),
                blog.getTitle(),
                blog.getCoverImage(),
                blog.getContent(),
                blog.getLiked(),
                blog.getComments(),
                blog.getCreateTime()
        );
    }
}
