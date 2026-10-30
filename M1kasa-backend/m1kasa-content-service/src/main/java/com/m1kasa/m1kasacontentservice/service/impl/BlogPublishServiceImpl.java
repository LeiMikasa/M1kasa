package com.m1kasa.m1kasacontentservice.service.impl;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.content.api.BlogPublishRequest;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogPublishService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class BlogPublishServiceImpl implements BlogPublishService {

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

    public BlogPublishServiceImpl(BlogMapper blogMapper, MinioTemplate minioTemplate) {
        this.blogMapper = blogMapper;
        this.minioTemplate = minioTemplate;
    }

    @Override
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
        //4.校验图片路径和归属
        if (!StringUtils.hasText(request.coverImage())){
            throw new IllegalArgumentException("请先上传图片");
        }
        String objectKey=request.coverImage().strip();
        String prefix="blog/"+userId+"/";
        if (!objectKey.startsWith(prefix)) {
            throw new IllegalArgumentException("只能使用本人上传的图片");
        }
        String fileName=objectKey.substring(prefix.length());

        if (!IMAGE_FILE_NAME.matcher(fileName).matches()){
            throw new IllegalArgumentException("图片路径不合法，请重新上传");
        }
        // 5. 确认图片实际存在
        if (!minioTemplate.exists(objectKey)) {
            throw new IllegalArgumentException("图片不存在，请重新上传");
        }
        //6.组装数据库实体
        LocalDateTime now=LocalDateTime.now();
        Blog blog = new Blog();
        blog.setUserId(userId);
        blog.setTitle(title);
        blog.setCoverImage(objectKey);
        blog.setContent(content);
        blog.setLiked(0);
        blog.setComments(0);
        blog.setStatus(PUBLISHED);
        blog.setCreateTime(now);
        blog.setUpdateTime(now);

        //7.保存笔记
        int rows = blogMapper.insert(blog);
        if (rows!=1){
            throw new IllegalStateException("笔记保存失败");
        }
        //AUTO自增主键会回填到实体
        return blog.getId();
    }
}
