package com.luckyflow.scheduler;

import com.luckyflow.service.ActivityPublicationIntentService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 对账过期发布意图，处理进程在 Redis 初始化后、MySQL 提交前退出的窗口。 */
@Component
@ConditionalOnProperty(prefix = "luckyflow.marketing.publication-recovery",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class ActivityPublicationRecoveryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityPublicationRecoveryScheduler.class);
    private final ActivityPublicationIntentService service;

    public ActivityPublicationRecoveryScheduler(ActivityPublicationIntentService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${luckyflow.marketing.publication-recovery.fixed-delay-millis:30000}")
    public void recover() {
        try { service.recoverBatch(Duration.ofMinutes(2), 50); }
        catch (RuntimeException failure) {
            LOG.error("Activity publication recovery scan failed", failure);
        }
    }
}
