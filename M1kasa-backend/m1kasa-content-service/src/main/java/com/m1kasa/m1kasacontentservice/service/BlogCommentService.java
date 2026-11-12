package com.m1kasa.m1kasacontentservice.service;

import com.m1kasa.content.api.BlogCommentPageDTO;
import org.springframework.stereotype.Service;


public interface BlogCommentService {
    Long publish(Long blogId,String content,Long replyToCommentId);
    BlogCommentPageDTO listByBlog(Long blogId, Long beforeId, int size);
    void delete(Long blogId, Long commentId);
    BlogCommentPageDTO listReplies(Long blogId, Long rootId, Long beforeId, int size);
}
