package com.m1kasa.m1kasapayservice.controller;

import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasapayservice.entity.MockChannelTrade;
import com.m1kasa.m1kasapayservice.service.MockPaymentChannelService;
import com.m1kasa.m1kasapayservice.service.MockPaymentNotifier;
import com.m1kasa.m1kasapayservice.web.MockPaymentConfirmDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/pay/mock/orders")
public class MockPaymentController {
    private static final Logger log=
            LoggerFactory.getLogger(MockPaymentController.class);

    private final MockPaymentChannelService channelService;
    private final MockPaymentNotifier notifier;

    public MockPaymentController(MockPaymentChannelService channelService, MockPaymentNotifier notifier) {
        this.channelService = channelService;
        this.notifier = notifier;
    }
    @PostMapping("/{payNo}/confirm")
    public ResponseEntity<Result<MockPaymentConfirmDTO>> confirm(
            @PathVariable("payNo")String payNo
    ){
        Long userId = UserContext.getUserId();
        if(userId==null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail("请先登录"));
        }
       try {
           //1.模拟收款
           MockChannelTrade trade = channelService.confirm(
                   payNo,
                   userId
           );
           //2.提交后台通知任务
           notifier.notifyPaidAsync(trade.getPayNo());
           //3.返回渠道收款凭证
           return ResponseEntity.ok(
                   Result.ok(
                           new MockPaymentConfirmDTO(
                                   trade.getPayNo(),
                                   trade.getChannelTradeNo(),
                                   trade.getPayAmountFen(),
                                   trade.getPaidTime()
                           )
                   )
           );
       }catch (IllegalArgumentException ex){
           return ResponseEntity.badRequest()
                   .body(Result.fail(ex.getMessage()));
       }catch (DataAccessException | IllegalStateException ex){
           log.error(
                   "模拟付款结果暂无法确认,payNo={}",
                   payNo,
                   ex
           );
           return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                   .body(Result.fail("付款结果暂无法确认，请查询后重试"));
       }
    }
}
