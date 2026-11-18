package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.service.Interface.DrawRequestRecoveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时恢复超时 INIT、待补偿 CANCELLED，以及尚未完成 Redis 成功终态确认的 SUCCESS 请求。 */
@Component
@ConditionalOnProperty(
        prefix = "luckyflow.lottery.recovery",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DrawRequestRecoveryScheduler {

    private final DrawRequestRecoveryService recoveryService;
    private final int batchSize;

    public DrawRequestRecoveryScheduler(
            DrawRequestRecoveryService recoveryService,
            @Value("${luckyflow.lottery.recovery.batch-size:100}") int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Recovery batch size must be positive");
        }
        this.recoveryService = recoveryService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${luckyflow.lottery.recovery.fixed-delay-millis:5000}")
    public void recover() {
        recoveryService.recoverBatch(batchSize);
    }
}
