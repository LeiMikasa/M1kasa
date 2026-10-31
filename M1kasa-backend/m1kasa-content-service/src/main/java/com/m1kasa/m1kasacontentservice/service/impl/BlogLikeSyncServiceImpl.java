package com.m1kasa.m1kasacontentservice.service.impl;

import com.m1kasa.content.api.message.BlogLikeChangedMessage;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogLike;
import com.m1kasa.m1kasacontentservice.mapper.BlogLikeMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogLikeSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class BlogLikeSyncServiceImpl implements BlogLikeSyncService {

    private static final int PUBLISHED=1;
    private static final long MAX_SAFE_INTEGER =
            9007199254740991L;

    private final BlogMapper blogMapper;
    private final BlogLikeMapper blogLikeMapper;

    public BlogLikeSyncServiceImpl(BlogMapper blogMapper, BlogLikeMapper blogLikeMapper) {
        this.blogMapper = blogMapper;
        this.blogLikeMapper = blogLikeMapper;
    }

    @Override
    @Transactional
    public void sync(BlogLikeChangedMessage message) {
        //1.校验消息参数
        validateMessage(message);

        Long blogId = message.blogId();
        Long userId= message.userId();

        //2.读取并锁定笔记记录
        Blog blog = blogMapper.selectByIdForUpdate(blogId);
        if (blog == null
                || !Integer.valueOf(PUBLISHED)
                .equals(blog.getStatus())) {
            throw new IllegalArgumentException(
                    "笔记不存在或已不可用"
            );
        }
        //3.读取并锁定当前用户的点赞关系
        BlogLike relation = blogLikeMapper.selectByBlogAndUserForUpdate(blogId, userId);
        //数据库没有关系记录时，按未点赞，版本0处理
        boolean oldLiked=false;
        long oldVersion=0L;
        if (relation != null) {
            if (relation.getLiked() == null
                    || relation.getVersion() == null
                    || relation.getVersion() < 0
                    || relation.getVersion() > MAX_SAFE_INTEGER) {
                throw new IllegalStateException(
                        "数据库点赞关系数据异常"
                );
            }

            oldLiked = relation.getLiked();
            oldVersion = relation.getVersion();
        }
        //4.重复消息或旧消息，直接忽略
        if (message.version()<=oldVersion){
            return;
        }
        //5.根据状态变化计算计数增量
        boolean newLiked=message.liked();
        int delta = (newLiked ? 1 : 0)
                - (oldLiked ? 1 : 0);
        //6.检查更新后的计数是否合法
        long currentCount = blog.getLiked() == null
                ? 0L
                : blog.getLiked().longValue();
        long newCount = currentCount + delta;

        if (currentCount < 0
                || newCount < 0
                || newCount > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "数据库点赞总数异常或超出范围"
            );
        }
        // 将消息中的毫秒时间戳转换为北京时间
        LocalDateTime eventTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(message.occurredAt()),
                ZoneOffset.ofHours(8)
        );
        //7.准备新增或更新点赞关系
        boolean newRecord=relation==null;
        if (newRecord){
            relation = new BlogLike();
            relation.setBlogId(blogId);
            relation.setUserId(userId);
            relation.setCreateTime(
                    LocalDateTime.now(ZoneOffset.ofHours(8))
            );
        }
        // 即使状态相同，也要保存更新的版本
        relation.setLiked(newLiked);
        relation.setVersion(message.version());
        relation.setUpdateTime(eventTime);
        // 8. 保存点赞关系
        int affected = newRecord
                ? blogLikeMapper.insert(relation)
                : blogLikeMapper.updateById(relation);

        if (affected != 1) {
            throw new IllegalStateException(
                    "保存点赞关系失败"
            );
        }
        // 9. 状态发生变化时，调整笔记总数
        if (delta != 0) {
            int updated = blogMapper.changeLiked(
                    blogId,
                    delta
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "更新笔记点赞总数失败"
                );
            }
        }

    }
    private void validateMessage(
            BlogLikeChangedMessage message
    ) {
        if (message == null
                || message.blogId() == null
                || message.blogId() <= 0
                || message.userId() == null
                || message.userId() <= 0
                || message.liked() == null
                || message.version() == null
                || message.version() <= 0
                || message.version() > MAX_SAFE_INTEGER
                || message.occurredAt() == null
                || message.occurredAt() <= 0
                || message.occurredAt() > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(
                    "点赞消息参数不合法"
            );
        }
    }
}
