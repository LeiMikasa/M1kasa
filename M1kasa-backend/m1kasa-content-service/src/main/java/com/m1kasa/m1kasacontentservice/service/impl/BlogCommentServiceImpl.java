package com.m1kasa.m1kasacontentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.content.api.BlogCommentDTO;
import com.m1kasa.content.api.BlogCommentPageDTO;
import com.m1kasa.m1kasacontentservice.entity.Blog;
import com.m1kasa.m1kasacontentservice.entity.BlogComment;
import com.m1kasa.m1kasacontentservice.mapper.BlogCommentMapper;
import com.m1kasa.m1kasacontentservice.mapper.BlogMapper;
import com.m1kasa.m1kasacontentservice.service.BlogCommentService;
import com.m1kasa.user.api.UserDTO;
import com.m1kasa.user.api.client.UserClient;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BlogCommentServiceImpl implements BlogCommentService {


    private final BlogMapper blogMapper;
    private final BlogCommentMapper commentMapper;
    private final UserClient userClient;
    public BlogCommentServiceImpl(BlogMapper blogMapper, BlogCommentMapper commentMapper, UserClient userClient) {
        this.blogMapper = blogMapper;
        this.commentMapper = commentMapper;
        this.userClient = userClient;
    }

    @Override
    @Transactional
    public Long publish(Long blogId, String content,Long replyToCommentId) {
        Long userId= UserContext.getUserId();
        if (userId==null||userId<=0){
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,"请先登录"
            );
        }
        if (blogId==null||blogId<=0){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,"笔记ID必须大于0"
            );
        }
        if (!StringUtils.hasText(content)){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,"请输入评论内容"
            );
        }
        String text=content.strip();
        if (text.codePointCount(0,text.length())>500){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,"评论不能超过500个字符"
            );
        }
        //给已发布的笔记加一条评论数
        if (blogMapper.incrementComments(blogId)!=1){
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "笔记不存在或不可评论"
            );
        }
        Long parentId = 0L;

        if (replyToCommentId != null) {
            if (replyToCommentId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "回复目标 ID 必须大于 0"
                );
            }

            BlogComment target = commentMapper.selectById(replyToCommentId);

            if (target == null
                    || !blogId.equals(target.getBlogId())
                    || !Integer.valueOf(1).equals(target.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "回复目标不存在或已删除"
                );
            }

            parentId = target.getParentId() == 0L
                    ? target.getId()
                    : target.getParentId();

            if (commentMapper.changeReplyCount(blogId, parentId, 1) != 1) {
                throw new IllegalStateException("更新回复数失败");
            }
        }
        BlogComment comment=new BlogComment();
        comment.setBlogId(blogId);
        comment.setUserId(userId);
        comment.setContent(text);
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.now());
        comment.setParentId(parentId);
        comment.setReplyToCommentId(replyToCommentId);
        if (commentMapper.insert(comment) != 1 || comment.getId() == null) {
            throw new IllegalStateException("保存评论失败");
        }

        return comment.getId();

    }

    @Override
    public BlogCommentPageDTO listByBlog(Long blogId, Long beforeId, int size) {
        return listPage(blogId, 0L, beforeId, size);
    }

    @Override
    public BlogCommentPageDTO listReplies(Long blogId, Long rootId, Long beforeId, int size) {
        if (rootId == null || rootId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "一级评论 ID 必须大于 0");
        }
        return listPage(blogId, rootId, beforeId, size);
    }

    private BlogCommentPageDTO listPage(Long blogId, Long parentId, Long beforeId, int size) {
        if (blogId == null || blogId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "笔记 ID 必须大于 0");
        }
        if (size < 1 || size > 50 || (beforeId != null && beforeId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }

        boolean published = blogMapper.exists(
                new LambdaQueryWrapper<Blog>()
                        .eq(Blog::getId, blogId)
                        .eq(Blog::getStatus, 1)
        );
        if (!published) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在");
        }

        LambdaQueryWrapper<BlogComment> query = new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getBlogId, blogId)
                .eq(BlogComment::getParentId, parentId);

        if (parentId == 0L) {
            // 一级评论已删除但还有回复时，保留这一行作为占位
            query.and(group -> group.eq(BlogComment::getStatus, 1)
                    .or()
                    .gt(BlogComment::getReplyCount, 0));
        } else {
            BlogComment root = commentMapper.selectById(parentId);
            if (root == null
                    || !blogId.equals(root.getBlogId())
                    || root.getParentId() != 0L
                    || (root.getStatus() == 0 && root.getReplyCount() == 0)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "一级评论不存在");
            }

            // 回复列表只返回可见的子评论
            query.eq(BlogComment::getStatus, 1);
        }

        if (parentId == 0L) {
            // 一级评论：最新在前，下一页找更小的ID
            query.lt(beforeId != null, BlogComment::getId, beforeId)
                    .orderByDesc(BlogComment::getId);
        } else {
            // 子评论：最早在前，下一页找更大的ID
            query.gt(beforeId != null, BlogComment::getId, beforeId)
                    .orderByAsc(BlogComment::getId);
        }

        List<BlogComment> rows = commentMapper.selectList(
                query.last("LIMIT " + (size + 1))
        );

        boolean hasMore = rows.size() > size;
        List<BlogComment> comments = hasMore ? rows.subList(0, size) : rows;
        List<BlogCommentDTO> records = toCommentRecords(blogId, comments);
        String nextCursor = hasMore ? records.get(records.size() - 1).id() : null;

        return new BlogCommentPageDTO(records, nextCursor, hasMore);
    }
    private List<BlogCommentDTO> toCommentRecords(Long blogId, List<BlogComment> comments) {
        List<Long> targetIds = comments.stream()
                .map(BlogComment::getReplyToCommentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Long> targetAuthors = new HashMap<>();
        if (!targetIds.isEmpty()) {
            List<BlogComment> targets = commentMapper.selectList(
                    new LambdaQueryWrapper<BlogComment>()
                            .select(BlogComment::getId, BlogComment::getUserId)
                            .eq(BlogComment::getBlogId, blogId)
                            .eq(BlogComment::getStatus, 1)
                            .in(BlogComment::getId, targetIds)
            );

            for (BlogComment target : targets) {
                targetAuthors.put(target.getId(), target.getUserId());
            }
        }

        Set<Long> authorIds = new HashSet<>(targetAuthors.values());
        comments.stream()
                .filter(comment -> comment.getStatus() == 1)
                .map(BlogComment::getUserId)
                .forEach(authorIds::add);

        Map<Long, UserDTO> authors = loadCommentAuthors(authorIds);

        return comments.stream().map(comment -> {
            String id = comment.getId().toString();

            if (comment.getStatus() == 0) {
                return new BlogCommentDTO(
                        id, null, null, null,
                        "该评论已删除", comment.getCreateTime(),
                        null, null, comment.getReplyCount(), true
                );
            }

            UserDTO author = authors.get(comment.getUserId());
            Long targetId = comment.getReplyToCommentId();
            String targetName = null;
            if (targetId != null) {
                Long targetAuthorId = targetAuthors.get(targetId);
                targetName = targetAuthorId == null
                        ? "原评论已删除"
                        : displayName(authors.get(targetAuthorId));
            }

            return new BlogCommentDTO(
                    id,
                    comment.getUserId().toString(),
                    displayName(author),
                    author == null ? null : author.icon(),
                    comment.getContent(),
                    comment.getCreateTime(),
                    targetId == null ? null : targetId.toString(),
                    targetName,
                    comment.getReplyCount(),
                    false
            );
        }).toList();
    }

    private Map<Long, UserDTO> loadCommentAuthors(Set<Long> ids) {
        Map<Long, UserDTO> authors = new HashMap<>();
        if (ids.isEmpty()) {
            return authors;
        }

        Result<List<UserDTO>> reply;
        try {
            reply = userClient.listPublicByIds(List.copyOf(ids));
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "查询评论作者失败", ex);
        }
        if (reply == null || !Boolean.TRUE.equals(reply.success()) || reply.data() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "查询评论作者失败");
        }

        for (UserDTO user : reply.data()) {
            if (user != null && user.id() != null) {
                authors.put(user.id(), user);
            }
        }
        return authors;
    }

    private String displayName(UserDTO user) {
        return user != null && StringUtils.hasText(user.nickName())
                ? user.nickName()
                : "未知用户";
    }

    @Override
    @Transactional
    public void delete(Long blogId, Long commentId) {
        Long userId = UserContext.getUserId();

        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }

        if (blogId == null || blogId <= 0 || commentId == null || commentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "笔记或评论 ID 不合法");
        }

        // 和发布评论保持相同的更新顺序，先更新笔记，再更新评论
        if (blogMapper.decrementComments(blogId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有可删除的评论");
        }

        if (commentMapper.markDeleted(blogId, commentId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有可删除的评论");
        }
        BlogComment deleted = commentMapper.selectById(commentId);
        Long parentId = deleted.getParentId();

        if (parentId > 0L
                && commentMapper.changeReplyCount(blogId, parentId, -1) != 1) {
            throw new IllegalStateException("更新回复数失败");
        }
    }


}
