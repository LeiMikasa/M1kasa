package com.m1kasa.m1kasaorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {
    @Update("""
        UPDATE tb_voucher_order
        SET status = #{targetStatus},
            pay_amount_fen = COALESCE(#{payAmountFen,jdbcType=BIGINT}, pay_amount_fen),
            pay_expire_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 MINUTE),
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
            @Param("payAmountFen") Long payAmountFen,
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

    @Select("""
        SELECT *
        FROM tb_voucher_order
        WHERE id = #{orderId}
        FOR UPDATE
        """)
    VoucherOrder selectByIdForUpdate(
            @Param("orderId") Long orderId
    );

    @Select("""
    SELECT *
    FROM tb_voucher_order
    WHERE status = #{waitingStatus}
      AND pay_expire_at <= CURRENT_TIMESTAMP
    ORDER BY pay_expire_at, id
    LIMIT #{limit}
    """)
    List<VoucherOrder> findExpiredWaitingPayment(
            @Param("waitingStatus") int waitingStatus,
            @Param("limit") int limit
    );
}
