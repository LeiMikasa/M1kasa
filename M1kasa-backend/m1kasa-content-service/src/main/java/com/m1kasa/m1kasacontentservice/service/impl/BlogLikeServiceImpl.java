package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.content.api.BlogLikeDTO;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogLike;
import com.m1kasa.m1kasacontentservice.mapper.BlogLikeMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogLikeService;

import java.time.LocalDateTime;

public class BlogLikeServiceImpl implements BlogLikeService {

    private static final int PUBLISHED=1;
    private final BlogMapper blogMapper;
    private final BlogLikeMapper blogLikeMapper;

    public BlogLikeServiceImpl(BlogMapper blogMapper, BlogLikeMapper blogLikeMapper) {
        this.blogMapper = blogMapper;
        this.blogLikeMapper = blogLikeMapper;
    }

    @Override
    public BlogLikeDTO setLiked(Long blogId, boolean liked) {
        //1.校验笔记ID
        if (blogId==null||blogId<=0){
            throw new IllegalArgumentException("笔记ID必须大于0");
        }
        //2.获取当前登录用户
        Long userId = UserContext.getUserId();

        if (userId==null||userId<=0){
            throw new IllegalStateException("当前用户上下文缺失");
        }
        //锁住笔记记录，直到当前事务结束
        Blog blog = blogMapper.selectByIdForUpdate(blogId);
        if (blog==null||!Integer.valueOf(PUBLISHED).equals(blog.getStatus())){
            throw new IllegalArgumentException("笔记不存在或已不可用");
        }
        int currentCount=blog.getLiked()==null?0:blog.getLiked();
        if (currentCount < 0) {
            throw new IllegalStateException("笔记点赞数异常");
        }
        //4.查询当前用户是否点过赞
        //固定的FOR UPDATE，是这里读取最新的点赞关系
        BlogLike existing = blogLikeMapper.selectOne(
                new LambdaQueryWrapper<BlogLike>()
                        .eq(BlogLike::getBlogId, blogId)
                        .eq(BlogLike::getUserId, userId)
                        .last("FOR UPDATE")
        );
        boolean alreadyLiked=existing!=null;
        //5.已经是目标状态，直接返回，不重复修改
        if (liked==alreadyLiked){
            return new BlogLikeDTO(alreadyLiked,currentCount);
        }
        //6.新增或者删除点赞关系
        int relationRows;
        if (liked){
            BlogLike relation=new BlogLike();
            relation.setBlogId(blogId);
            relation.setUserId(userId);
            relation.setCreateTime(LocalDateTime.now());

            relationRows=blogLikeMapper.insert(relation);
        }else {
            relationRows=blogLikeMapper.deleteById(existing.getId());
        }

        if (relationRows!=1){
            throw new IllegalStateException("修改点赞关系失败");
        }
        // 7. 同步修改笔记点赞总数
        int delta = liked ? 1 : -1;

        int countRows = blogMapper.changeLiked(blogId, delta);

        if (countRows != 1) {
            throw new IllegalStateException("更新点赞总数失败");
        }

        // 8. 返回修改后的状态和总数
        return new BlogLikeDTO(liked, currentCount + delta);

    }
}
