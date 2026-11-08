package com.m1kasa.m1kasapayservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("""
            SELECT *
            FROM tb_payment_order
            WHERE pay_no = #{payNo}
            FOR UPDATE
            """)
    PaymentOrder selectByPayNoForUpdate(
            @Param("payNo") String payNo
    );
}
