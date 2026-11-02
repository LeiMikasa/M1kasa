package com.m1kasa.m1kasacontentservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_blog_like_dead_letter")
public class BlogLikeDeadLetter {

    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    //消息指纹
    private String messageKey;
    //原消息ID
    private String messageId;
    //原业务队列
    private String sourceQueue;
    //原始消息体的Base64
    private String bodyBase64;
    private String contentType;
    private String contentEncoding;
    //序列化后的消息头
    private String headersJson;
    //死信原因，例如rejected
    private String deathReason;
    //0待处理，1确认已重发，2.人工忽略
    private Integer status;
    //采集版本，与点赞状态版本不同
    private Long captureVersion;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer replayCount;

    public Integer getReplayCount() {
        return replayCount;
    }

    public void setReplayCount(Integer replayCount) {
        this.replayCount = replayCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSourceQueue() {
        return sourceQueue;
    }

    public void setSourceQueue(String sourceQueue) {
        this.sourceQueue = sourceQueue;
    }

    public String getBodyBase64() {
        return bodyBase64;
    }

    public void setBodyBase64(String bodyBase64) {
        this.bodyBase64 = bodyBase64;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public void setHeadersJson(String headerJson) {
        this.headersJson = headerJson;
    }

    public String getDeathReason() {
        return deathReason;
    }

    public void setDeathReason(String deathReason) {
        this.deathReason = deathReason;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getCaptureVersion() {
        return captureVersion;
    }

    public void setCaptureVersion(Long captureVersion) {
        this.captureVersion = captureVersion;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
