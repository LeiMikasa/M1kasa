package com.m1kasa.voucher.api.client;

import com.m1kasa.voucher.api.SeckillStockDeductRequest;
import com.m1kasa.voucher.api.SeckillStockDeductResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "m1kasa-voucher-service")
public interface VoucherStockClient {

    @PostMapping("/internal/seckill/stock/deduct")
    SeckillStockDeductResult deduct(@RequestBody SeckillStockDeductRequest request);
}
