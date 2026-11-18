package com.luckyflow.notification.scheduler;

import com.luckyflow.notification.config.NotificationProperties;
import com.luckyflow.notification.service.NotificationDispatchService;
import com.luckyflow.notification.service.NotificationRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时触发通知领取及超时 attempt 恢复，实际网络发送由独立线程池执行。 */
@Component
@ConditionalOnProperty(
        prefix = "luckyflow.notification",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationDispatchScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchScheduler.class);

    private final NotificationDispatchService notificationDispatchService;
    private final NotificationRecoveryService notificationRecoveryService;
    private final NotificationProperties properties;

    public NotificationDispatchScheduler(
            NotificationDispatchService notificationDispatchService,
            NotificationRecoveryService notificationRecoveryService,
            NotificationProperties properties
    ) {
        properties.validate();
        this.notificationDispatchService = notificationDispatchService;
        this.notificationRecoveryService = notificationRecoveryService;
        this.properties = properties;
    }

    /** 周期性领取到期 PENDING 记录；本方法不等待服务商响应。 */
    @Scheduled(fixedDelayString = "${luckyflow.notification.dispatch-fixed-delay-millis:5000}")
    public void dispatch() {
        try {
            notificationDispatchService.dispatchBatch(properties.getBatchSize());
        } catch (RuntimeException failure) {
            LOGGER.error("Notification dispatch scan failed", failure);
        }
    }

    /** 周期性收敛超时 PROCESSING attempt，并保留后续自动重试或人工处理能力。 */
    @Scheduled(fixedDelayString = "${luckyflow.notification.recovery-fixed-delay-millis:30000}")
    public void recoverTimedOut() {
        try {
            notificationRecoveryService.recoverTimedOut(
                    properties.getProcessingTimeout(),
                    properties.getBatchSize()
            );
        } catch (RuntimeException failure) {
            LOGGER.error("Notification timeout recovery scan failed", failure);
        }
    }
}
