package com.m1kasa.m1kasapayservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_payment_trade")
public class PaymentTrade {

    @TableId(value = "pay_no", type = IdType.INPUT)
    private String payNo;

    private String channelTradeNo;
    private Long payAmountFen;
    private LocalDateTime paidTime;
    private LocalDateTime createTime;

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public String getChannelTradeNo() {
        return channelTradeNo;
    }

    public void setChannelTradeNo(String channelTradeNo) {
        this.channelTradeNo = channelTradeNo;
    }

    public Long getPayAmountFen() {
        return payAmountFen;
    }

    public void setPayAmountFen(Long payAmountFen) {
        this.payAmountFen = payAmountFen;
    }

    public LocalDateTime getPaidTime() {
        return paidTime;
    }

    public void setPaidTime(LocalDateTime paidTime) {
        this.paidTime = paidTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
