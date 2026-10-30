package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.content.api.BlogLikeDTO;
import com.m1kasa.m1kasacontentservice.cache.BlogLikeCacheInitializer;
import com.m1kasa.m1kasacontentservice.constant.BlogLikeRedisKeys;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogLike;
import com.m1kasa.m1kasacontentservice.mapper.BlogLikeMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogLikeService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class BlogLikeServiceImpl implements BlogLikeService {
    //Lua返回数组，用list接收
    private static final DefaultRedisScript<List> SET_SCRIPT=new DefaultRedisScript<>();
    static {
        SET_SCRIPT.setLocation(new ClassPathResource("lua/blog_like_set.lua"));
        SET_SCRIPT.setResultType(List.class);
    }
    private final StringRedisTemplate stringRedisTemplate;
    private final BlogLikeCacheInitializer initializer;

    public BlogLikeServiceImpl(StringRedisTemplate stringRedisTemplate, BlogLikeCacheInitializer initializer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.initializer = initializer;
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
            throw new IllegalStateException(
                    "当前用户上下文缺失"
            );
        }
        //3.先尝试执行点赞脚本
        List<?> result = executeScript(blogId, userId, liked);
        int code = readCode(result);
        //4.只有数据未初始化时，才会读取数据库并初始化
        if (code==-2){
            initializer.initialize(blogId,userId);
            //初始化完成后，只重试一次
            result=executeScript(blogId,userId,liked);
            code=readCode(result);
        }
        //5.状态发生变化，或已经是目标状态，都算处理成功
        if (code==0||code==1){
            return toDTO(result,liked);
        }
        //6.其他结果停止处理，不循环重试
        if (code == -1) {
            throw new IllegalArgumentException(
                    "点赞请求参数不合法"
            );
        }

        String message = switch (code) {
            case -2 -> "点赞数据尚未就绪，请稍后重试";
            case -3 -> "点赞缓存 Key 类型错误";
            case -4 -> "点赞缓存状态异常，需要恢复";
            case -5 -> "点赞计数或版本超出允许范围";
            default -> "点赞脚本返回未知状态";
        };

        throw new IllegalStateException(message);
    }

    @Override
    public BlogLikeDTO getLikeState(Long blogId) {
        //1.校验笔记ID
        if (blogId==null||blogId<=0){
            throw new IllegalArgumentException("笔记ID必须大于0");
        }
        //2.获取当前登录用户
        Long userId = UserContext.getUserId();
        if (userId==null || userId<=0){
            throw new IllegalStateException("当前用户上下文缺失");
        }
        //3.先读取redis
        BlogLikeDTO result = readLikeStateFromRedis(blogId, userId);
        if (result!=null){
            return result;
        }
        //4.数据尚未初始化，加载数据库数据
        initializer.initialize(blogId,userId);
        //5.初始化后重新读取一次
        result=readLikeStateFromRedis(blogId,userId);
        if (result==null){
            throw new IllegalStateException("点赞数据尚未就绪，请稍后重试");
        }
        return result;

    }
    private BlogLikeDTO readLikeStateFromRedis(
        Long blogId,
        Long userId
    ){
        //查询该用户的状态，例如1:3:1793361600000
        String state = stringRedisTemplate.<String, String>opsForHash()
                .get(
                        BlogLikeRedisKeys.state(blogId), userId.toString()
                );
        //查询笔记当前点赞总数，例如 21
        String countText = stringRedisTemplate.opsForValue().get(BlogLikeRedisKeys.count(blogId));

        //缺少数据，交给上层初始化
        if (state==null || countText==null){
            return null;
        }
        // 已存在但格式错误时，停止处理
        if (!state.matches("[01]:\\d+:\\d+")
                || !countText.matches("\\d+")) {
            throw new IllegalStateException(
                    "点赞缓存数据格式异常"
            );
        }
        try {
            int count = Integer.parseInt(countText);

            return new BlogLikeDTO(
                    state.charAt(0) == '1',
                    count
            );

        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "点赞总数超出允许范围",
                    ex
            );
        }
    }

    //执行脚本
    private List<?> executeScript(
        Long blogId,
        Long userId,
        boolean liked
    ){
        return stringRedisTemplate.execute(
          SET_SCRIPT,
          List.of(
                  BlogLikeRedisKeys.state(blogId),
                  BlogLikeRedisKeys.count(blogId),
                  BlogLikeRedisKeys.outbox(blogId)
          ),
          userId.toString(),
          liked?"1":"0",
          Long.toString(System.currentTimeMillis())
        );
    }
    //读取lua返回数组中的第一个值：处理结果码
    private int readCode(List<?> result){
        if (result==null||result.isEmpty()||!(result.get(0) instanceof Long)){
            throw new IllegalStateException("点赞脚本返回格式异常");
        }
        long code=(Long) result.get(0);
        if (code<-5||code>1){
            throw new IllegalStateException("点赞脚本返回未知状态码: "+code);
        }
        return (int) code;
    }

    //把结果转换成前端需要的dto
    private BlogLikeDTO toDTO(
            List<?> result,
            boolean targetLiked
    ){
        if (result.size() != 4
                || !(result.get(1) instanceof Long)
                || !(result.get(2) instanceof Long)
                || !(result.get(3) instanceof Long)) {
            throw new IllegalStateException(
                    "点赞脚本成功结果格式异常"
            );
        }
        long currentLiked = (Long) result.get(1);
        long currentCount = (Long) result.get(2);
        long currentVersion = (Long) result.get(3);

        if ((currentLiked != 0 && currentLiked != 1)
                || currentCount < 0
                || currentCount > Integer.MAX_VALUE
                || currentVersion < 0
                || currentVersion > 9007199254740991L
                || ((currentLiked == 1) != targetLiked)) {
            throw new IllegalStateException(
                    "点赞脚本返回的数据不合法"
            );
        }

        return new BlogLikeDTO(
                currentLiked == 1,
                (int) currentCount
        );
    }
}
