package com.m1kasa.m1kasaorderservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasaorderservice.service.OrderQueryService;
import com.m1kasa.order.api.OrderSummaryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderQueryController {

    private final OrderQueryService orderQueryService;

    public OrderQueryController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping("/of/voucher/{voucherId}")
    public ResponseEntity<Result<OrderSummaryDTO>> findByVoucher(
            @PathVariable("voucherId") Long voucherId
    ) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }
        if (voucherId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Result.fail("优惠券 ID 必须大于 0"));
        }

        // 尚未落库时 data 为 null，前端可以继续轮询。
        return ResponseEntity.ok(Result.ok(
                orderQueryService.findByVoucher(voucherId, userId)
        ));
    }
}
