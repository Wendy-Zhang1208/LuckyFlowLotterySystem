package com.luckyflow.scheduler;

import com.luckyflow.marketing.service.ActivityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 按服务器业务时钟把已发布活动以 CAS 推进到 RUNNING/ENDED。 */
@Component
@ConditionalOnProperty(
        prefix = "luckyflow.marketing.lifecycle",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ActivityLifecycleScheduler {

    private final ActivityService activityService;
    private final int batchSize;

    public ActivityLifecycleScheduler(
            ActivityService activityService,
            @Value("${luckyflow.marketing.lifecycle.batch-size:100}") int batchSize
    ) {
        if (batchSize <= 0 || batchSize > 1_000) {
            throw new IllegalArgumentException("Activity lifecycle batch size must be between 1 and 1000");
        }
        this.activityService = activityService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${luckyflow.marketing.lifecycle.fixed-delay-millis:30000}")
    public void synchronize() {
        activityService.synchronizeLifecycle(batchSize);
    }
}
