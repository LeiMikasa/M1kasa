package com.m1kasa.m1kasacontentservice.mq;

import com.m1kasa.m1kasacontentservice.constant.BlogLikeRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "m1kasa.blog.like.outbox",
        name = "enabled",
        havingValue = "true"
)
public class BlogLikeOutboxJob {
    private static final Logger log= LoggerFactory.getLogger(BlogLikeOutboxJob.class);

    private static final String OUTBOX_PREFIX="m1kasa:blog:like:outbox:{";

    private final StringRedisTemplate stringRedisTemplate;
    private final BlogLikeOutboxDispatcher dispatcher;

    public BlogLikeOutboxJob(StringRedisTemplate stringRedisTemplate, BlogLikeOutboxDispatcher dispatcher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 2000,initialDelay = 5000)
    public void dispatchPending(){
        ScanOptions options = ScanOptions.scanOptions()
                .match(OUTBOX_PREFIX + "*}")
                .count(100)
                .build();
        //第一层：查找各篇笔记的outbox key
        try (Cursor<String> keys = stringRedisTemplate.scan(options)){
            while (keys.hasNext()){
                String outboxkey = keys.next();
                try {
                    //从key中提取blogId
                    String idText = outboxkey.substring(
                            OUTBOX_PREFIX.length(),
                            outboxkey.length() - 1
                    );
                    Long blogId = Long.valueOf(idText);
                    if (blogId<=0 || !outboxkey.equals(BlogLikeRedisKeys.outbox(blogId))){
                        throw new IllegalArgumentException("点赞Outbox Key不合法");
                    }
                    dispatchBlog(blogId, outboxkey);
                }catch (RuntimeException ex){
                    log.warn("处理点赞Outbox失败，key={}",
                            outboxkey,ex);
                }
            }
        }catch (RuntimeException ex){
            log.warn("扫描点赞Outbox Key失败",ex);
        }

    }
    private void dispatchBlog(
            Long blogId,
            String outboxKey
    ){
        ScanOptions options = ScanOptions.scanOptions().count(100).build();
        //第二层 读取当前笔记Outbox Hash中的记录
        try (Cursor<Map.Entry<String, String>> entries
                     = stringRedisTemplate.<String,String>opsForHash().scan(outboxKey,options)){
            while (entries.hasNext()){
                Map.Entry<String, String> entry = entries.next();
                try {
                    Long userId = Long.valueOf(entry.getKey());
                    if (!userId.toString().equals(entry.getKey())) {
                        throw new IllegalArgumentException(
                                "Outbox 用户 ID 格式不合法"
                        );
                    }
                    String originalState = entry.getValue();

                    //等待本条消息的发布确认和清理流程完成
                    dispatcher.dispatchOne(
                            blogId,userId,originalState
                    ).join();
                }catch (RuntimeException ex){
                    // 失败记录保留，后续扫描继续尝试
                    log.warn(
                            "投递点赞记录失败，blogId={}, userId={}",
                            blogId,
                            entry.getKey(),
                            ex);
                }
            }
        }
    }
}
