package com.m1kasa.m1kasavoucherservice.service;

import com.m1kasa.m1kasavoucherservice.web.SeckillVoucherCreateRequest;
import com.m1kasa.voucher.api.VoucherDTO;

import java.util.List;

public interface VoucherService {
    List<VoucherDTO> listByShopId(Long id);

    Long createSeckillVoucher(SeckillVoucherCreateRequest request);
}
