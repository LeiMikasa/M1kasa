package com.m1kasa.m1kasavoucherservice.service.impl;

import com.m1kasa.m1kasavoucherservice.constant.SeckillRedisKeys;
import com.m1kasa.m1kasavoucherservice.constant.SeckillStockResultStatus;
import com.m1kasa.m1kasavoucherservice.entity.SeckillStockDeduction;
import com.m1kasa.m1kasavoucherservice.mapper.SeckillStockDeductionMapper;
import com.m1kasa.m1kasavoucherservice.service.SeckillStockCompensationService;
import com.m1kasa.order.api.message.SeckillOrderClosedMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class SeckillStockCompensationServiceImpl implements SeckillStockCompensationService {

    private static final String SUPPORTED_FAILURE_CODE="STOCK_UNAVAILABLE";
    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT=new DefaultRedisScript<>();
    static {
        COMPENSATE_SCRIPT.setLocation(
                new ClassPathResource("/lua/seckill_compensate.lua")
        );
        COMPENSATE_SCRIPT.setResultType(Long.class);
    }
    private final SeckillStockDeductionMapper deductionMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public SeckillStockCompensationServiceImpl(SeckillStockDeductionMapper deductionMapper, StringRedisTemplate stringRedisTemplate) {
        this.deductionMapper = deductionMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void compensate(SeckillOrderClosedMessage message) {
          //1.检查消息参数
          validateMessage(message);
          //2.核对数据库中已经提交的库存拒绝结果
          verifyRejectedRecord(message);
        // 3. 调用 Lua，检查凭证并执行一次补偿
        Long result = stringRedisTemplate.execute(
                COMPENSATE_SCRIPT,
                List.of(
                        SeckillRedisKeys.activity(message.voucherId()),
                        SeckillRedisKeys.reservation(
                                message.voucherId(),
                                message.requestId()
                        )
                ),
                message.requestId(),
                message.userId().toString()
        );
        if (result==null){
            throw new IllegalStateException("库存补偿未返回结果");
        }
        // 4. 只有补偿成功或已经补偿，才正常结束
        switch (Math.toIntExact(result)) {
            case 0, 1 -> {
                return;
            }
            case 2 -> throw new IllegalStateException(
                    "预占凭证不存在，无法自动补偿"
            );
            case 3 -> throw new IllegalStateException(
                    "预占凭证与补偿消息不匹配"
            );
            case 4 -> throw new IllegalStateException(
                    "当前预占状态不支持补偿"
            );
            case 5 -> throw new IllegalStateException(
                    "补偿参数或 Redis 数据异常"
            );
            default -> throw new IllegalStateException(
                    "库存补偿脚本返回未知结果：" + result
            );
        }

    }
    private void validateMessage(SeckillOrderClosedMessage message) {
        if (message == null
                || !StringUtils.hasText(message.requestId())
                || message.requestId().length() > 64
                || message.orderId() == null
                || message.orderId() <= 0
                || message.voucherId() == null
                || message.voucherId() <= 0
                || message.userId() == null
                || message.userId() <= 0) {
            throw new IllegalArgumentException("订单关闭事件参数不合法");
        }

        // 当前只支持“数据库未扣减成功”这一种补偿
        if (!SUPPORTED_FAILURE_CODE.equals(message.failureCode())) {
            throw new IllegalArgumentException(
                    "不支持的库存补偿原因：" + message.failureCode()
            );
        }
    }

    private void verifyRejectedRecord(
            SeckillOrderClosedMessage message
    ) {
        SeckillStockDeduction record =
                deductionMapper.selectById(message.orderId());

        if (record == null) {
            throw new IllegalStateException(
                    "库存处理记录不存在，无法确认是否应该补偿"
            );
        }

        // 必须是同一订单、同一请求、同一优惠券、同一用户
        boolean sameRequest =
                Objects.equals(record.getOrderId(), message.orderId())
                        && Objects.equals(record.getRequestId(), message.requestId())
                        && Objects.equals(record.getVoucherId(), message.voucherId())
                        && Objects.equals(record.getUserId(), message.userId());

        if (!sameRequest) {
            throw new IllegalStateException(
                    "库存处理记录与订单关闭事件不一致"
            );
        }

        // 数据库扣减成功的请求，不能进入这条补偿分支
        if (!Integer.valueOf(SeckillStockResultStatus.REJECTED)
                .equals(record.getResultStatus())) {
            throw new IllegalStateException(
                    "库存处理结果不是明确拒绝，禁止执行此类补偿"
            );
        }

        if (!Objects.equals(
                record.getFailureCode(),
                message.failureCode()
        )) {
            throw new IllegalStateException(
                    "库存拒绝原因与订单关闭事件不一致"
            );
        }
    }
}
