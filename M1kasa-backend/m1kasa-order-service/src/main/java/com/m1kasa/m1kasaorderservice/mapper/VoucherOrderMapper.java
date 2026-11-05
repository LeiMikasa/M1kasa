package com.m1kasa.m1kasaorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    @Update("""
            UPDATE tb_voucher_order
            SET status = #{targetStatus},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{orderId}
              AND request_id = #{requestId}
              AND voucher_id = #{voucherId}
              AND user_id = #{userId}
              AND status = #{expectedStatus}
            """)
    int confirmStock(
            @Param("orderId") Long orderId,
            @Param("requestId") String requestId,
            @Param("voucherId") Long voucherId,
            @Param("userId") Long userId,
            @Param("expectedStatus") Integer expectedStatus,
            @Param("targetStatus") Integer targetStatus
    );
    @Update("""
        UPDATE tb_voucher_order
        SET status = #{targetStatus},
            failure_code = #{failureCode},
            update_time = CURRENT_TIMESTAMP
        WHERE id = #{orderId}
          AND request_id = #{requestId}
          AND voucher_id = #{voucherId}
          AND user_id = #{userId}
          AND status = #{expectedStatus}
        """)
    int rejectStock(
            @Param("orderId") Long orderId,
            @Param("requestId") String requestId,
            @Param("voucherId") Long voucherId,
            @Param("userId") Long userId,
            @Param("failureCode") String failureCode,
            @Param("expectedStatus") Integer expectedStatus,
            @Param("targetStatus") Integer targetStatus
    );
}
