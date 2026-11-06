package com.m1kasa.m1kasavoucherservice.controller;

import com.m1kasa.m1kasavoucherservice.service.SeckillStockService;
import com.m1kasa.voucher.api.SeckillStockDeductRequest;
import com.m1kasa.voucher.api.SeckillStockDeductResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/seckill/stock")
public class InternalSeckillStockController {
    private final SeckillStockService stockService;
    private final String stockRpcToken;

    public InternalSeckillStockController(
            SeckillStockService stockService,
            @Value("${m1kasa.seckill.stock-rpc.token:}") String stockRpcToken) {
        this.stockService = stockService;
        this.stockRpcToken = stockRpcToken;
    }
    @PostMapping("/deduct")
    public SeckillStockDeductResult deduct(
            @RequestHeader(value = "X-Stock-Rpc-Token", required = false)
            String token,
            @RequestBody SeckillStockDeductRequest request
    ){
         if (!StringUtils.hasText(stockRpcToken)
         || token==null
         || !MessageDigest.isEqual(
                 stockRpcToken.getBytes(StandardCharsets.UTF_8),
                 token.getBytes(StandardCharsets.UTF_8)
         )){
             throw new ResponseStatusException(
                     HttpStatus.FORBIDDEN,
                     "库存 RPC 鉴权失败"
             );
         }
         return stockService.deductForRpc(request);
    }
}
