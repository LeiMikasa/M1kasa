package com.m1kasa.m1kasauserservice.cache;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasauserservice.constant.FollowRedisKeys;
import com.m1kasa.m1kasauserservice.entity.Follow;
import com.m1kasa.m1kasauserservice.mapper.FollowMapper;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class FollowCacheInitializer {
    private static final DefaultRedisScript<Long> INIT_SCRIPT =
            new DefaultRedisScript<>();

    static {
        INIT_SCRIPT.setLocation(
                new ClassPathResource("lua/follow_cache_init.lua")
        );
        INIT_SCRIPT.setResultType(Long.class);
    }

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public FollowCacheInitializer(UserMapper userMapper, FollowMapper followMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED
    )
    public void initialize(Long userId){
        // 1. 校验当前用户 ID
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须大于 0");
        }
        // 2. 锁住当前用户行，持有到事务结束
        Long lockedUserId = userMapper.selectIdForUpdate(userId);

        if (lockedUserId == null) {
            throw new IllegalStateException("当前用户不存在");
        }

        String followingKey = FollowRedisKeys.following(userId);
        //3.等待锁期间，其他请求可能已经完成初始化，因此再次检查
        Boolean initialized = stringRedisTemplate.opsForSet()
                .isMember(
                        followingKey,
                        FollowRedisKeys.INITIALIZED_MEMBER
                );
        if (Boolean.TRUE.equals(initialized)){
            return;
        }
        //4.查询当前用户完整的关注列表，只取被关注者ID
        List<Follow> relations = followMapper.selectList(
                new LambdaQueryWrapper<Follow>()
                        .select(Follow::getFollowUserId)
                        .eq(Follow::getUserId, userId)
        );
        //5.按Lua要求组装
        List<String> args=new ArrayList<>();
        args.add(FollowRedisKeys.INITIALIZED_MEMBER);
        args.add(
                Long.toString(FollowRedisKeys.CACHE_TTL.toMillis())
        );
        for (Follow relation : relations) {
            Long targetUserId = relation.getFollowUserId();

            if (targetUserId == null || targetUserId <= 0) {
                throw new IllegalStateException("数据库关注关系不合法");
            }

            args.add(targetUserId.toString());
        }
        // 6. 执行初始化脚本
        Long result = stringRedisTemplate.execute(
                INIT_SCRIPT,
                List.of(followingKey),
                args.toArray()
        );
        // 7. 检查脚本执行结果
        if (result == null) {
            throw new IllegalStateException("关注缓存初始化未返回结果");
        }

        if (result == 0L || result == 1L) {
            return;
        }

        if (result == -1L) {
            throw new IllegalStateException("关注缓存 Key 类型错误");
        }

        if (result == -2L) {
            throw new IllegalStateException("关注缓存初始化参数错误");
        }

        throw new IllegalStateException(
                "关注缓存初始化失败，返回码：" + result
        );
    }


}
