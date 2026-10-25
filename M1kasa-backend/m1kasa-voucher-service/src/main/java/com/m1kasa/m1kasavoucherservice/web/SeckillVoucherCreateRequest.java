package com.m1kasa.m1kasavoucherservice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SeckillVoucherCreateRequest(
        @NotNull(message = "店铺 ID 不能为空")
        @Positive(message = "店铺 ID 必须大于 0")
        Long shopId,

        @NotBlank(message = "优惠券标题不能为空")
        @Size(max = 255, message = "标题不能超过 255 个字符")
        String title,

        @Size(max = 255, message = "副标题不能超过 255 个字符")
        String subTitle,

        @Size(max = 1024, message = "使用规则不能超过 1024 个字符")
        String rules,

        @NotNull(message = "支付金额不能为空")
        @Positive(message = "支付金额必须大于 0")
        Long payValue,

        @NotNull(message = "抵扣金额不能为空")
        @Positive(message = "抵扣金额必须大于 0")
        Long actualValue,

        @NotNull(message = "库存不能为空")
        @Positive(message = "库存必须大于 0")
        Integer stock,

        @NotNull(message = "开始时间不能为空")
        LocalDateTime beginTime,

        @NotNull(message = "结束时间不能为空")
        LocalDateTime endTime


) {}
