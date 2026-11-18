package com.luckyflow.config.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** 为 Outbox 发布与超时恢复提供不受其他定时任务阻塞的专用调度线程。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "luckyflow.outbox", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfiguration {

    public static final String OUTBOX_TASK_SCHEDULER = "outboxTaskScheduler";

    // 仅供显式指定的 Outbox 任务使用；保留 Boot 默认调度器，避免其他任务占用恢复线程。
    @Bean(name = OUTBOX_TASK_SCHEDULER, defaultCandidate = false)
    public ThreadPoolTaskScheduler outboxTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 发布可能阻塞在网络 I/O；至少保留另一条线程，使 Recovery 仍能按时回收 PROCESSING。
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("outbox-scheduling-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        return scheduler;
    }
}
