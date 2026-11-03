package com.m1kasa.m1kasacontentservice.service.impl;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.content.api.BlogPublishRequest;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogImage;
import com.m1kasa.m1kasacontentservice.mapper.BlogImageMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogPublishService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class BlogPublishServiceImpl implements BlogPublishService {
    //最大照片数量
    private static final int MAX_IMAGES = 9;
    private static final int MAX_TITLE_LENGTH=60;
    private static final int MAX_CONTENT_LENGTH=10_000;
    private static final int PUBLISHED=1;

    // 与上传接口生成的 UUID 文件名格式保持一致
    private static final Pattern IMAGE_FILE_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"
    );
    private final BlogMapper blogMapper;
    private final MinioTemplate minioTemplate;
    private final BlogImageMapper blogImageMapper;
    public BlogPublishServiceImpl(BlogMapper blogMapper, MinioTemplate minioTemplate, BlogImageMapper blogImageMapper) {
        this.blogMapper = blogMapper;
        this.minioTemplate = minioTemplate;
        this.blogImageMapper = blogImageMapper;
    }

    @Override
    @Transactional
    public Long publish(BlogPublishRequest request) {
        //1.获取当前登录用户
        Long userId= UserContext.getUserId();
        if (userId==null || userId<=0){
            throw new IllegalStateException("当前用户上下文缺失");
        }
        if (request==null){
            throw new IllegalArgumentException("发布内容不能为空");
        }
        // 2. 校验标题
        if (!StringUtils.hasText(request.title())) {
            throw new IllegalArgumentException("请输入笔记标题");
        }

        String title = request.title().strip();

        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("标题不能超过 60 个字符");
        }

        // 3. 校验正文
        if (!StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("请输入笔记正文");
        }
        String content = request.content().strip();
        if (content.length()>MAX_CONTENT_LENGTH){
            throw new IllegalArgumentException("正文不能超过10000个字符");
        }
        //4.校验全部图片
        List<String> imageKeys = validateImages(userId, request);
        //5.保存笔记主记录，第一张图片作为封面
        LocalDateTime now= LocalDateTime.now();
        Blog blog=new Blog();
        blog.setUserId(userId);
        blog.setTitle(title);
        blog.setCoverImage(imageKeys.get(0));
        blog.setContent(content);
        blog.setLiked(0);
        blog.setComments(0);
        blog.setStatus(PUBLISHED);
        blog.setCreateTime(now);
        blog.setUpdateTime(now);
        if (blogMapper.insert(blog)!=1||blog.getId()==null){
            throw new IllegalStateException("保存笔记失败");
        }
        //6.保存所有图片，包括作为封面的第一张
        for (int i=0;i<imageKeys.size();i++){
            BlogImage image=new BlogImage();
            image.setBlogId(blog.getId());
            image.setObjectKey(imageKeys.get(i));
            image.setSortOrder(i);
            image.setCreateTime(now);

            if (blogImageMapper.insert(image)!=1){
                throw new IllegalStateException("保存笔记图片失败");
            }
        }
        return blog.getId();
    }
    //图片列表校验方法
    private List<String> validateImages(
            Long userId,
            BlogPublishRequest request
    ) {
        List<String> submitted = request.imageObjectKeys();

        // 1. 兼容原来的单图请求
        if (submitted == null) {
            if (!StringUtils.hasText(request.coverImage())) {
                throw new IllegalArgumentException("请先上传图片");
            }

            submitted = List.of(request.coverImage());
        }

        // 2. 限制图片数量
        if (submitted.isEmpty() || submitted.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("每篇笔记需要 1～9 张图片");
        }

        String prefix = "blog/" + userId + "/";

        List<String> imageKeys = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 3. 校验路径格式、归属和重复图片
        for (String submittedKey : submitted) {
            if (!StringUtils.hasText(submittedKey)) {
                throw new IllegalArgumentException("图片路径不能为空");
            }

            String key = submittedKey.strip();

            if (!key.startsWith(prefix)) {
                throw new IllegalArgumentException("只能使用本人上传的图片");
            }

            String fileName = key.substring(prefix.length());

            if (!IMAGE_FILE_NAME.matcher(fileName).matches()) {
                throw new IllegalArgumentException(
                        "图片路径不合法，请重新上传"
                );
            }

            if (!seen.add(key)) {
                throw new IllegalArgumentException("不能重复添加同一张图片");
            }

            imageKeys.add(key);
        }

        // 4. 同时提交封面和图片列表时，要求封面就是第一张
        if (request.imageObjectKeys() != null
                && StringUtils.hasText(request.coverImage())
                && !request.coverImage().strip().equals(imageKeys.get(0))) {
            throw new IllegalArgumentException("封面必须是图片列表中的第一张");
        }

        // 5. 确认每张图片确实存在于 MinIO
        for (String key : imageKeys) {
            if (!minioTemplate.exists(key)) {
                throw new IllegalArgumentException(
                        "部分图片不存在，请重新上传"
                );
            }
        }

        return imageKeys;
    }
}
