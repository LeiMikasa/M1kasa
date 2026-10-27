package com.m1kasa.m1kasavoucherservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasavoucherservice.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    @Update("""
      UPDATE tb_seckill_voucher
      SET stock = stock - 1,
          update_time = CURRENT_TIMESTAMP
      WHERE voucher_id = #{voucherId}
        AND stock > 0
""")
    int deductStock(@Param("voucherId") Long voucherId);
}
