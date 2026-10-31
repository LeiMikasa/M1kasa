package com.m1kasa.m1kasacontentservice.mq;


import com.m1kasa.content.api.message.BlogLikeChangedMessage;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeRedisKeys;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class BlogLikeOutboxDispatcher {

    private static final long MAX_SAFE_INTEGER=
            9007199254740991L;

    private static final DefaultRedisScript<Long> ACK_SCRIPT =
            new DefaultRedisScript<>();

    static {
        ACK_SCRIPT.setLocation(
                new ClassPathResource(
                        "lua/blog_like_outbox_ack.lua"
                )
        );
        ACK_SCRIPT.setResultType(Long.class);
    }

    private final BlogLikePublisher publisher;
    private final StringRedisTemplate stringRedisTemplate;

    public BlogLikeOutboxDispatcher(BlogLikePublisher publisher, StringRedisTemplate stringRedisTemplate) {
        this.publisher = publisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public CompletableFuture<Void> dispatchOne(
        Long blogId,
        Long userId,
        String originalState
    ){
        try {
            //将redis中的原始记录转换成消息对象
            BlogLikeChangedMessage message=toMessage(blogId,userId,originalState);
            //发布成功后，才执行清理
            return publisher.send(message)
                    .thenRun(() -> {
                        Long deleted = stringRedisTemplate.execute(
                                        ACK_SCRIPT,
                                        List.of(BlogLikeRedisKeys.outbox(blogId)),
                                        userId.toString(),
                                        originalState
                                );
                                if (deleted == null
                                        || (deleted != 0L && deleted != 1L)) {
                                    throw new IllegalStateException(
                                            "清理点赞 Outbox 返回结果异常"
                                    );
                                }

                    });
        }catch (RuntimeException ex){
            return CompletableFuture.failedFuture(ex);
        }
    }

    private BlogLikeChangedMessage toMessage(
            Long blogId,
            Long userId,
            String originalState
    ){
        if (blogId == null || blogId <= 0
                || userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "笔记 ID 和用户 ID 必须大于 0"
            );
        }
        // 例如：1:3:1793361600000
        if (originalState == null
                || !originalState.matches("[01]:\\d+:\\d+")) {
            throw new IllegalArgumentException(
                    "点赞 Outbox 记录格式错误"
            );
        }
        String[] parts=originalState.split(":");
        boolean liked="1".equals(parts[0]);
        long version=Long.parseLong(parts[1]);
        long occurredAt=Long.parseLong(parts[2]);
        if (version <= 0 || version > MAX_SAFE_INTEGER
                || occurredAt <= 0
                || occurredAt > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(
                    "点赞 Outbox 版本或时间不合法"
            );
        }
        return new BlogLikeChangedMessage(
                blogId,
                userId,
                liked,
                version,
                occurredAt
        );
    }

}
