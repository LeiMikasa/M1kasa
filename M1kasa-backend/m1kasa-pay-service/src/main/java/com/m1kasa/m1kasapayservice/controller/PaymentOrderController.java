package com.m1kasa.m1kasapayservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import com.m1kasa.m1kasapayservice.service.PaymentOrderService;
import com.m1kasa.m1kasapayservice.web.PaymentCreateRequest;
import com.m1kasa.m1kasapayservice.web.PaymentOrderDTO;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay/orders")
public class PaymentOrderController {
    private static final Logger log=
            LoggerFactory.getLogger(PaymentOrderController.class);
    private final PaymentOrderService paymentOrderService;

    public PaymentOrderController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }
    @PostMapping
    public ResponseEntity<Result<PaymentOrderDTO>> create(
            @RequestBody PaymentCreateRequest request
            ){
        Long userId= UserContext.getUserId();
        if (userId==null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }
        if (request==null){
            return ResponseEntity.badRequest()
                    .body(Result.fail("请求不能为空"));
        }
        try {
            PaymentOrder payment = paymentOrderService.create(
                    request.orderId(),
                    userId
            );
            return ResponseEntity.ok(
                    Result.ok(new PaymentOrderDTO(
                            payment.getPayNo(),
                            payment.getOrderId().toString(),
                            payment.getPayAmountFen(),
                            payment.getStatus(),
                            payment.getCreateTime(),
                            payment.getPaidTime()
                    ))
            );
        }catch (IllegalArgumentException ex){
            return ResponseEntity.badRequest()
                    .body(Result.fail(ex.getMessage()));
        }catch (FeignException ex){
            if (ex.status()==404){
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Result.fail("订单不存在"));
            }
            if (ex.status()==409){
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.fail("当前订单不可支付"));
            }
            log.warn("创建支付单时订单 RPC 失败，orderId={}", request.orderId(), ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail("订单查询暂不可用，请稍后重试"));
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("创建支付单失败，orderId={}", request.orderId(), ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Result.fail("支付单暂时不可用，请稍后重试"));
        }

    }
}
