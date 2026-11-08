package com.m1kasa.m1kasapayservice.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasapayservice.constant.PaymentStatus;
import com.m1kasa.m1kasapayservice.entity.PaymentOrder;
import com.m1kasa.m1kasapayservice.mapper.PaymentOrderMapper;
import com.m1kasa.m1kasapayservice.service.PaymentReconcileService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("local")
@ConditionalOnProperty(
        prefix = "m1kasa.pay.reconcile",
        name = "enabled",
        havingValue = "true"
)
public class PaymentReconcileJob {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentReconcileJob.class);

    private static final int BATCH_SIZE = 100;

    private final PaymentOrderMapper paymentOrderMapper;
    private final PaymentReconcileService reconcileService;

    private long lastScannedId;
    private long cycleUpperBound;
    private LocalDateTime cycleCutoff;

    public PaymentReconcileJob(
            PaymentOrderMapper paymentOrderMapper,
            PaymentReconcileService reconcileService
    ) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.reconcileService = reconcileService;
    }

    @XxlJob("paymentReconcile")
    public synchronized void reconcilePending() {
        // 1. 开始新一轮扫描，固定时间条件和最大ID
        if (cycleUpperBound == 0) {
            cycleCutoff = LocalDateTime.now().minusSeconds(30);

            PaymentOrder latest = paymentOrderMapper.selectOne(
                    new LambdaQueryWrapper<PaymentOrder>()
                            .select(PaymentOrder::getId)
                            .eq(
                                    PaymentOrder::getStatus,
                                    PaymentStatus.WAITING_PAYMENT
                            )
                            .eq(PaymentOrder::getChannel, "MOCK")
                            .le(PaymentOrder::getCreateTime, cycleCutoff)
                            .orderByDesc(PaymentOrder::getId)
                            .last("LIMIT 1")
            );

            if (latest == null) {
                resetScan();
                XxlJobHelper.log("当前没有需要补查的支付单");
                return;
            }

            cycleUpperBound = latest.getId();
        }

        // 2. 从上次位置继续，最多查询100条
        List<PaymentOrder> records = paymentOrderMapper.selectList(
                new LambdaQueryWrapper<PaymentOrder>()
                        .select(
                                PaymentOrder::getId,
                                PaymentOrder::getPayNo
                        )
                        .eq(
                                PaymentOrder::getStatus,
                                PaymentStatus.WAITING_PAYMENT
                        )
                        .eq(PaymentOrder::getChannel, "MOCK")
                        .le(PaymentOrder::getCreateTime, cycleCutoff)
                        .gt(PaymentOrder::getId, lastScannedId)
                        .le(PaymentOrder::getId, cycleUpperBound)
                        .orderByAsc(PaymentOrder::getId)
                        .last("LIMIT " + BATCH_SIZE)
        );

        if (records.isEmpty()) {
            resetScan();
            XxlJobHelper.log("本轮扫描结束，下次从头扫描");
            return;
        }

        int confirmed = 0;
        int pending = 0;
        int failures = 0;

        // 3. 逐条主动查单
        for (PaymentOrder record : records) {
            if (Thread.currentThread().isInterrupted()) {
                XxlJobHelper.handleFail("查单任务被中断");
                return;
            }

            lastScannedId = record.getId();

            try {
                boolean paid = reconcileService.reconcile(
                        record.getPayNo()
                );

                if (paid) {
                    confirmed++;
                } else {
                    pending++;
                }
            } catch (RuntimeException ex) {
                failures++;

                log.warn(
                        "主动查单异常，payNo={}",
                        record.getPayNo(),
                        ex
                );
            }
        }

        // 4. 到达本轮终点，下一次重新检查旧记录
        if (lastScannedId >= cycleUpperBound) {
            resetScan();
        }

        XxlJobHelper.log(
                "查单结束：扫描={}，确认已支付={}，仍待确认={}，异常={}",
                records.size(),
                confirmed,
                pending,
                failures
        );

        if (failures > 0) {
            XxlJobHelper.handleFail("本批查单异常数=" + failures);
        }
    }

    private void resetScan() {
        lastScannedId = 0;
        cycleUpperBound = 0;
        cycleCutoff = null;
    }
}
