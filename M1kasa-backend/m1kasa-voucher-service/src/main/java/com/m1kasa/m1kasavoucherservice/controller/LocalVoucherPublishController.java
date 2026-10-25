package com.m1kasa.m1kasavoucherservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasavoucherservice.service.VoucherService;
import com.m1kasa.m1kasavoucherservice.web.SeckillVoucherCreateRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("local")
@RestController
@RequestMapping("/voucher")
public class LocalVoucherPublishController {

    private final VoucherService voucherService;

    public LocalVoucherPublishController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping("/seckill")
    public ResponseEntity<Result<Long>> createSeckillVoucher(
            @Valid @RequestBody SeckillVoucherCreateRequest request
    ) {
        Long voucherId = voucherService.createSeckillVoucher(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(voucherId));
    }
}
