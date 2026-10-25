package com.m1kasa.m1kasavoucherservice.controller;


import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasavoucherservice.service.VoucherService;
import com.m1kasa.voucher.api.VoucherDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/voucher")
public class VoucherController {
    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping("/list/{shopId}")
    public ResponseEntity<Result<List<VoucherDTO>>> listByShopId(
            @PathVariable("shopId") Long shopId
    ){
        if (shopId<=0){
            return ResponseEntity.badRequest()
                    .body(Result.fail("店铺ID必须大于0"));
        }
        List<VoucherDTO> vouchers = voucherService.listByShopId(shopId);

        return ResponseEntity.ok(Result.ok(vouchers));
    }
}
