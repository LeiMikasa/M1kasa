package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.common.storage.MinioTemplate;
import com.m1kasa.content.api.*;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogImage;
import com.m1kasa.m1kasacontentservice.mapper.BlogImageMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogQueryService;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.client.UserClient;
import org.apache.catalina.util.StringUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BlogQueryServiceImpl implements BlogQueryService {

    private static final int PUBLISHED=1;
    private static final int IMAGE_URL_EXPIRY_SECONDS = 600;
    private final BlogMapper blogMapper;
    private final UserClient userClient;
    private final MinioTemplate minioTemplate;
    private final BlogImageMapper blogImageMapper;
    public BlogQueryServiceImpl(BlogMapper blogMapper, UserClient userClient, MinioTemplate minioTemplate, BlogImageMapper blogImageMapper) {
        this.blogMapper = blogMapper;
        this.userClient = userClient;
        this.minioTemplate = minioTemplate;
        this.blogImageMapper = blogImageMapper;
    }

    @Override
    public BlogDetailDTO findById(Long id) {
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
        Result<List<UserDTO>> reply = userClient.listPublicByIds(
                List.of(blog.getUserId())
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
        // 查询全部图片链接
        List<String> imageUrls = findImageUrls(blog);
        // 第一张图片作为封面
        String coverImageUrl = imageUrls.isEmpty()
                ? null
                : imageUrls.get(0);

        return new BlogDetailDTO(
                blog.getId(),
                blog.getShopId(),
                blog.getUserId(),
                author == null ? "未知用户" : author.nickName(),
                author == null ? null : author.icon(),
                blog.getTitle(),
                coverImageUrl,
                imageUrls,
                blog.getContent(),
                blog.getLiked(),
                blog.getComments(),
                blog.getCreateTime()
        );

    }

    @Override
    public BlogPageDTO findMyBlogs(int current, int size) {
        Long userId = UserContext.getUserId();

        if (userId == null || userId <= 0) {
            throw new IllegalStateException("当前用户上下文缺失");
        }

        // 我的笔记：把当前登录用户 ID 传给共用查询
        return findUserBlogs(userId, current, size);
    }

    @Override
    public BlogPageDTO findUserBlogs(
            Long userId,
            int current,
            int size
    ) {
        // 1. 校验作者 ID 和分页参数
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须大于 0");
        }

        if (current < 1 || size < 1 || size > 50) {
            throw new IllegalArgumentException(
                    "页码必须大于 0，每页条数必须在 1～50 之间"
            );
        }

        // 2. 分页查询指定作者已发布的笔记
        Page<Blog> page = new Page<>(current, size);

        blogMapper.selectPage(
                page,
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
                        .orderByDesc(
                                Blog::getCreateTime,
                                Blog::getId
                        )
        );

        // 3. 将数据库实体转换为返回 DTO
        List<BlogSummaryDTO> records = page.getRecords()
                .stream()
                .map(blog -> {
                    String coverImageUrl = null;

                    if (StringUtils.hasText(blog.getCoverImage())) {
                        coverImageUrl =
                                minioTemplate.getPresignedGetUrl(
                                        blog.getCoverImage(),
                                        IMAGE_URL_EXPIRY_SECONDS
                                );
                    }

                    return new BlogSummaryDTO(
                            blog.getId().toString(),
                            blog.getTitle(),
                            coverImageUrl,
                            blog.getLiked() == null ? 0 : blog.getLiked(),
                            blog.getComments() == null ? 0 : blog.getComments(),
                            blog.getCreateTime()
                    );
                })
                .toList();

        // 4. 返回记录和分页信息
        return new BlogPageDTO(
                records,
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    @Override
    public BlogFeedPageDTO findFeed(int current, int size) {
        //1.校验分页参数
        if (current<1||size<1||size>50){
            throw new IllegalArgumentException( "页码必须大于0，每页条数必须在1到50之间");
        }
        //2.分页查询所有已发布笔记
        Page<Blog> page=new Page<>(current,size);
        blogMapper.selectPage(
                page,
                new LambdaQueryWrapper<Blog>()
                        .select(
                                Blog::getId,
                                Blog::getUserId,
                                Blog::getTitle,
                                Blog::getCoverImage,
                                Blog::getLiked,
                                Blog::getComments,
                                Blog::getCreateTime
                        )
                        .eq(Blog::getStatus, PUBLISHED)
                        .orderByDesc(
                                Blog::getCreateTime,
                                Blog::getId
                        )
        );
        List<Blog> blogs=page.getRecords();
        //3.当前页没有记录，直接返回
        if (blogs.isEmpty()){
            return new BlogFeedPageDTO(
                    List.of(),
                    page.getTotal(),
                    page.getCurrent(),
                    page.getSize()
            );
        }
        //4.手机作者ID并去重
        List<Long> authorIds = blogs.stream()
                .map(Blog::getUserId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        Map<Long,UserDTO> authorMap=new HashMap<>();
        //5.一次批量RPC查询作者资料
        if (!authorIds.isEmpty()){
            Result<List<UserDTO>> reply = userClient.listPublicByIds(
                    authorIds
            );
            if (reply==null || !Boolean.TRUE.equals(reply.success())
            || reply.data()==null){
                throw new IllegalStateException("用户服务返回异常结果");
            }
            for (UserDTO user : reply.data()) {
                if (user!=null&&user.id()!=null){
                    authorMap.put(user.id(),user);
                }
            }
        }
        //6.组装
        List<BlogFeedItemDTO> records = blogs.stream()
                .map(blog -> {
                    UserDTO author = authorMap.get(
                            blog.getUserId()
                    );

                    String authorName =
                            author != null
                                    && StringUtils.hasText(author.nickName())
                                    ? author.nickName()
                                    : "未知用户";

                    String authorIcon = author == null
                            ? null
                            : author.icon();

                    String coverImageUrl = null;

                    if (StringUtils.hasText(blog.getCoverImage())) {
                        coverImageUrl =
                                minioTemplate.getPresignedGetUrl(
                                        blog.getCoverImage(),
                                        IMAGE_URL_EXPIRY_SECONDS
                                );
                    }

                    return new BlogFeedItemDTO(
                            blog.getId().toString(),
                            blog.getUserId() == null
                                    ? null
                                    : blog.getUserId().toString(),
                            authorName,
                            authorIcon,
                            blog.getTitle(),
                            coverImageUrl,
                            blog.getLiked() == null
                                    ? 0
                                    : blog.getLiked(),
                            blog.getComments() == null
                                    ? 0
                                    : blog.getComments(),
                            blog.getCreateTime()
                    );
                })
                .toList();
        // 7. 返回分页结果
        return new BlogFeedPageDTO(
                records,
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }
    private List<String> findImageUrls(Blog blog){
        //1.查询这篇笔记的图片，按顺序排列
        List<BlogImage> images = blogImageMapper.selectList(
                new LambdaQueryWrapper<BlogImage>()
                        .select(
                                BlogImage::getObjectKey,
                                BlogImage::getSortOrder
                        )
                        .eq(BlogImage::getBlogId, blog.getId())
                        .orderByAsc(BlogImage::getSortOrder)
        );
        List<String> objectKeys;
        //2.兼容旧笔记，没有图片明细时，使用原来的封面
        if (images.isEmpty()){
            objectKeys = StringUtils.hasText(blog.getCoverImage())
                    ? List.of(blog.getCoverImage())
                    : List.of();
        }else {
            objectKeys = images.stream()
                    .map(BlogImage::getObjectKey)
                    .toList();
        }
        // 3. 把文件路径转换成临时访问链接
        return objectKeys.stream()
                .map(key -> {
                    if (!StringUtils.hasText(key)) {
                        throw new IllegalStateException(
                                "笔记图片记录缺少文件路径"
                        );
                    }

                    return minioTemplate.getPresignedGetUrl(
                            key,
                            IMAGE_URL_EXPIRY_SECONDS
                    );
                })
                .toList();
    }

}
