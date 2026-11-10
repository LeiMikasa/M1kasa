package com.m1kasa.m1kasashopservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

// 指定这个实体对应的数据库表
@TableName("tb_shop_search_outbox")
public class ShopSearchOutbox {

    // 使用数据库自增主键
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // 本次修改的消息 ID，重发时沿用
    private String requestId;

    // 被修改的店铺 ID
    private Long shopId;

    // 本次修改后的店铺版本
    private Long shopVersion;

    // 待发送消息的 JSON，包含完整店铺快照
    private String payload;

    // 0：待发送；1：已通过 MQ 发布确认
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getShopId() {
        return shopId;
    }

    public void setShopId(Long shopId) {
        this.shopId = shopId;
    }

    public Long getShopVersion() {
        return shopVersion;
    }

    public void setShopVersion(Long shopVersion) {
        this.shopVersion = shopVersion;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
