package com.m1kasa.m1kasavoucherservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasavoucherservice.enums.SeckillReserveStatus;
import com.m1kasa.m1kasavoucherservice.service.VoucherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher/seckill")
public class SeckillController {
    private final VoucherService voucherService;

    public SeckillController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/{voucherId}/reserve")
    public ResponseEntity<Result<String>> reserve(
            @PathVariable("voucherId") Long voucherId
    ){
        Long userId= UserContext.getUserId();
        if (userId==null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }
        SeckillReserveStatus status = voucherService.reserveSeckill(voucherId, userId);

        return switch (status) {
            case RESERVED -> ResponseEntity.ok(
                    Result.ok("已获得抢券资格，订单尚未创建")
            );

            case SOLD_OUT -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Result.fail("优惠券已抢完"));

            case DUPLICATE -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Result.fail("请勿重复抢券"));

            case NOT_STARTED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Result.fail("活动尚未开始"));

            case ENDED -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Result.fail("活动已经结束"));

            case NOT_INITIALIZED -> ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail("活动暂不可用，请稍后重试"));
        };

    }


}
