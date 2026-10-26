package com.m1kasa.m1kasaorderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderQueryService;

import com.m1kasa.order.api.OrderSummaryDTO;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final VoucherOrderMapper voucherOrderMapper;

    public OrderQueryServiceImpl(VoucherOrderMapper voucherOrderMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
    }

    @Override
    public OrderSummaryDTO findByVoucher(Long voucherId, Long userId) {
        if (voucherId == null || voucherId <= 0 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 或优惠券 ID 不合法");
        }

        VoucherOrder order = voucherOrderMapper.selectOne(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId)
        );

        if (order == null) {
            return null;
        }

        return new OrderSummaryDTO(
                order.getId().toString(),
                order.getVoucherId().toString(),
                order.getStatus(),
                order.getCreateTime()
        );
    }
}
