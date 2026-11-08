package com.m1kasa.m1kasapayservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasapayservice.entity.PaymentTrade;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentTradeMapper extends BaseMapper<PaymentTrade> {
}
