package com.luckyflow.notification.config;

import com.luckyflow.notification.sender.SmsSender;
import com.luckyflow.notification.sender.impl.UnconfiguredSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 通知发送线程池与尚未接入的短信供应商兜底配置。 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {

    /**
     * 外部发送使用独立有界线程池，避免慢服务商阻塞调度扫描，也避免无界任务堆积耗尽内存。
     */
    @Bean(name = "notificationSendExecutor")
    public Executor notificationSendExecutor(NotificationProperties properties) {
        properties.validate();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-send-");
        executor.setCorePoolSize(properties.getExecutorCorePoolSize());
        executor.setMaxPoolSize(properties.getExecutorMaxPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    /** 未配置真实短信供应商时明确失败，绝不把日志输出伪装成发送成功。 */
    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender unconfiguredSmsSender() {
        return new UnconfiguredSmsSender();
    }
}
