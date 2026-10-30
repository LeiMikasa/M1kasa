package com.m1kasa.m1kasacontentservice.cache;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeRedisKeys;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogLike;
import com.m1kasa.m1kasacontentservice.mapper.BlogLikeMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;

@Component
public class BlogLikeCacheInitializer {
    private static final int PUBLISHED=1;
    private static final DefaultRedisScript<Long> INIT_SCRIPT=new DefaultRedisScript<>();

    static {
        INIT_SCRIPT.setLocation(
                new ClassPathResource("lua/blog_like_init.lua")
        );
        INIT_SCRIPT.setResultType(Long.class);
    }
    private final BlogMapper blogMapper;
    private final BlogLikeMapper blogLikeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public BlogLikeCacheInitializer(BlogMapper blogMapper, BlogLikeMapper blogLikeMapper, StringRedisTemplate stringRedisTemplate) {
        this.blogMapper = blogMapper;
        this.blogLikeMapper = blogLikeMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW
    )
    public void initialize(Long blogId, Long userId) {
        //1.校验参数
        if (blogId==null||blogId<=0||userId==null||userId<=0){
            throw new IllegalArgumentException(
                    "笔记id和用户id必须大于0"
            );
        }
        //2.查询笔记状态和数据库中的电脑总数
        Blog blog = blogMapper.selectOne(
                new LambdaQueryWrapper<Blog>()
                        .select(
                                Blog::getId,
                                Blog::getStatus,
                                Blog::getLiked
                        )
                        .eq(Blog::getId, blogId)
        );
        if (blog==null||!Integer.valueOf(PUBLISHED).equals(blog.getStatus())){
            throw new IllegalArgumentException(
                    "笔记不存在或已不可用"
            );
        }
        int initialCount=blog.getLiked()==null?0:blog.getLiked();
        if (initialCount<0){
            throw new IllegalStateException("数据库点赞总数异常");
        }
        //3.查询当前用户的点赞关系
        //未点赞的记录也要查询，以便恢复版本号
        BlogLike relation = blogLikeMapper.selectOne(
                new LambdaQueryWrapper<BlogLike>()
                        .select(
                                BlogLike::getLiked,
                                BlogLike::getVersion,
                                BlogLike::getUpdateTime
                        )
                        .eq(BlogLike::getBlogId, blogId)
                        .eq(BlogLike::getUserId, userId)
        );
        // 从来没有记录：未点赞、版本0、时间0
        boolean initialLiked = false;
        long initialVersion = 0L;
        long initialTime = 0L;
        if (relation != null) {
            if (relation.getLiked() == null
                    || relation.getVersion() == null
                    || relation.getVersion() < 0) {
                throw new IllegalStateException(
                        "数据库点赞关系数据异常"
                );
            }

            initialLiked = relation.getLiked();
            initialVersion = relation.getVersion();

            if (relation.getUpdateTime() != null) {
                // 数据库中的 LocalDateTime 按北京时间解释
                initialTime = relation.getUpdateTime()
                        .toInstant(ZoneOffset.ofHours(8))
                        .toEpochMilli();
            }
        }
        // 4. 将数据库数据交给 Lua，按需初始化 Redis
        Long result = stringRedisTemplate.execute(
                INIT_SCRIPT,
                List.of(
                        BlogLikeRedisKeys.state(blogId),
                        BlogLikeRedisKeys.count(blogId),
                        BlogLikeRedisKeys.outbox(blogId)
                ),
                userId.toString(),
                Integer.toString(initialCount),
                initialLiked ? "1" : "0",
                Long.toString(initialVersion),
                Long.toString(initialTime)
        );
        // 5. 初始化成功，或数据已经存在，都可以继续
        if (result == null) {
            throw new IllegalStateException(
                    "点赞缓存初始化未返回结果"
            );
        }

        if (result == 0L || result == 1L) {
            return;
        }

        if (result == -2L) {
            throw new IllegalStateException(
                    "点赞缓存数据不完整，需要恢复"
            );
        }

        if (result == -3L) {
            throw new IllegalStateException(
                    "点赞缓存 Key 类型错误"
            );
        }

        throw new IllegalStateException(
                "点赞缓存初始化失败，返回码：" + result
        );
    }
}
