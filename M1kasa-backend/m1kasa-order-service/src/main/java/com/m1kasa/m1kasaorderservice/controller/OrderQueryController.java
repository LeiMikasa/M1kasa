package com.m1kasa.m1kasaorderservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasaorderservice.service.OrderQueryService;
import com.m1kasa.order.api.OrderPageDTO;
import com.m1kasa.order.api.OrderSummaryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/of/me")
    public ResponseEntity<Result<OrderPageDTO>> findMyOrders(
        @RequestParam(name = "current",defaultValue = "1")
        int current,
        @RequestParam(name = "size",defaultValue = "10")
        int size
    ){
        //从登录上下文获取用户身份
        Long userId=UserContext.getUserId();

        if (userId==null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }
        // 拒绝不合理的分页参数
        if (current < 1 || size < 1 || size > 50) {
            return ResponseEntity.badRequest()
                    .body(Result.fail(
                            "页码必须大于 0，每页条数必须在 1 到 50 之间"
                    ));
        }

        OrderPageDTO page = orderQueryService.findMyOrders(
                userId,
                current,
                size
        );

        return ResponseEntity.ok(Result.ok(page));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Result<OrderSummaryDTO>> findById(
            @PathVariable("orderId") Long orderId
    ) {
        Long userId = UserContext.getUserId();

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }

        if (orderId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Result.fail("订单 ID 必须大于 0"));
        }

        OrderSummaryDTO order = orderQueryService.findById(
                orderId,
                userId
        );

        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("订单不存在"));
        }

        return ResponseEntity.ok(Result.ok(order));
    }
}
