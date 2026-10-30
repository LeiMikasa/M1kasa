package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.content.api.BlogDetailDTO;
import com.m1kasa.content.api.BlogPageDTO;
import com.m1kasa.content.api.BlogSummaryDTO;
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
    private static final int IMAGE_URL_EXPIRY_SECONDS = 600;
    private final BlogMapper blogMapper;
    private final UserClient userClient;
    private final MinioTemplate minioTemplate;

    public BlogQueryServiceImpl(BlogMapper blogMapper, UserClient userClient, MinioTemplate minioTemplate) {
        this.blogMapper = blogMapper;
        this.userClient = userClient;
        this.minioTemplate = minioTemplate;
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
        String coverImageUrl = minioTemplate.getPresignedGetUrl(
                blog.getCoverImage(),
                IMAGE_URL_EXPIRY_SECONDS
        );
        return new BlogDetailDTO(
                blog.getId(),
                blog.getShopId(),
                blog.getUserId(),
                author==null?"未知用户":author.nickName(),
                author==null?null:author.icon(),
                blog.getTitle(),
                coverImageUrl,
                blog.getContent(),
                blog.getLiked(),
                blog.getComments(),
                blog.getCreateTime()
        );
    }

    @Override
    public BlogPageDTO findMyBlogs(int current, int size) {
        //1.获取当前用户身份
        Long userId= UserContext.getUserId();

        if (userId==null||userId<=0){
            throw new IllegalStateException("当前用户上下文缺失");
        }
        //2.校验分页参数
        if (current<1||size<1||size>50){
            throw new IllegalArgumentException("页码必须大于0，每页条数必须在1到50之间");
        }
        //3.创建分页对象
        Page<Blog> page=new Page<>(current,size);
        //4.只查询当前用户已发布的笔记
        LambdaQueryWrapper<Blog> query =
                new LambdaQueryWrapper<Blog>()
                        .select(
                                Blog::getId,
                                Blog::getTitle,
                                Blog::getCoverImage,
                                Blog::getLiked,
                                Blog::getComments,
                                Blog::getCreateTime
                        )
                        .eq(Blog::getUserId, userId)
                        .eq(Blog::getStatus, PUBLISHED)
                        .orderByDesc(Blog::getCreateTime)
                        .orderByDesc(Blog::getId);
        blogMapper.selectPage(page, query);
        //5.将实体转换为DTO
        List<BlogSummaryDTO> records = page.getRecords().stream().map(blog -> new BlogSummaryDTO(
                blog.getId().toString(),
                blog.getTitle(),
                minioTemplate.getPresignedGetUrl(
                        blog.getCoverImage(),
                        IMAGE_URL_EXPIRY_SECONDS
                ),
                blog.getLiked(),
                blog.getComments(),
                blog.getCreateTime()
        )).toList();
        // 6. 返回当前页和分页信息
        return new BlogPageDTO(
                records,
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }
}
