package com.m1kasa.m1kasaorderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderQueryService;

import com.m1kasa.order.api.OrderPageDTO;
import com.m1kasa.order.api.OrderSummaryDTO;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Override
    public OrderPageDTO findMyOrders(Long userId, int current, int size) {
        if (userId==null || userId<=0){
            throw new IllegalArgumentException("用户ID不合法");
        }
        if (current<1||size<1||size>50){
            throw new IllegalArgumentException(
                    "页码必须大于 0，每页条数必须在 1 到 50 之间"
            );
        }
        //指定当前页码，每页条数，默认会查询总记录数
        Page<VoucherOrder> page=new Page<>(current,size);
        voucherOrderMapper.selectPage(
                page,
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getUserId,userId)
                        .orderByDesc(
                                VoucherOrder::getCreateTime,
                                VoucherOrder::getId
                        )
        );
        // 将数据库实体转换为对外返回的 DTO
        List<OrderSummaryDTO> records = page.getRecords()
                .stream()
                .map(order -> new OrderSummaryDTO(
                        order.getId().toString(),
                        order.getVoucherId().toString(),
                        order.getStatus(),
                        order.getCreateTime()
                ))
                .toList();

        return new OrderPageDTO(
                records,
                page.getTotal(),
                current,
                size
        );
    }

    @Override
    public OrderSummaryDTO findById(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单 ID 不合法");
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 不合法");
        }

        VoucherOrder order = voucherOrderMapper.selectOne(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getId, orderId)
                        .eq(VoucherOrder::getUserId, userId)
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
