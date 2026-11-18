package com.luckyflow.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 通知领取、发送和超时恢复所使用的可调运行参数。 */
@ConfigurationProperties(prefix = "luckyflow.notification")
public class NotificationProperties {

    private boolean schedulingEnabled = true;
    private int batchSize = 50;
    private long dispatchFixedDelayMillis = 5_000L;
    private long recoveryFixedDelayMillis = 30_000L;
    private Duration processingTimeout = Duration.ofMinutes(2);
    private Duration providerTimeout = Duration.ofSeconds(10);
    private int executorCorePoolSize = 2;
    private int executorMaxPoolSize = 4;
    private int executorQueueCapacity = 100;
    private String emailFrom = "no-reply@luckyflow.local";

    public boolean isSchedulingEnabled() {
        return schedulingEnabled;
    }

    public void setSchedulingEnabled(boolean schedulingEnabled) {
        this.schedulingEnabled = schedulingEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getDispatchFixedDelayMillis() {
        return dispatchFixedDelayMillis;
    }

    public void setDispatchFixedDelayMillis(long dispatchFixedDelayMillis) {
        this.dispatchFixedDelayMillis = dispatchFixedDelayMillis;
    }

    public long getRecoveryFixedDelayMillis() {
        return recoveryFixedDelayMillis;
    }

    public void setRecoveryFixedDelayMillis(long recoveryFixedDelayMillis) {
        this.recoveryFixedDelayMillis = recoveryFixedDelayMillis;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public Duration getProviderTimeout() {
        return providerTimeout;
    }

    public void setProviderTimeout(Duration providerTimeout) {
        this.providerTimeout = providerTimeout;
    }

    public int getExecutorCorePoolSize() {
        return executorCorePoolSize;
    }

    public void setExecutorCorePoolSize(int executorCorePoolSize) {
        this.executorCorePoolSize = executorCorePoolSize;
    }

    public int getExecutorMaxPoolSize() {
        return executorMaxPoolSize;
    }

    public void setExecutorMaxPoolSize(int executorMaxPoolSize) {
        this.executorMaxPoolSize = executorMaxPoolSize;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public String getEmailFrom() {
        return emailFrom;
    }

    public void setEmailFrom(String emailFrom) {
        this.emailFrom = emailFrom;
    }

    /** 在创建后台线程和定时任务前拒绝无界或无效配置。 */
    public void validate() {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Notification batch size must be positive");
        }
        if (dispatchFixedDelayMillis <= 0 || recoveryFixedDelayMillis <= 0) {
            throw new IllegalArgumentException("Notification scheduler delays must be positive");
        }
        if (!isPositive(processingTimeout) || !isPositive(providerTimeout)) {
            throw new IllegalArgumentException("Notification timeouts must be positive");
        }
        if (executorCorePoolSize <= 0
                || executorMaxPoolSize < executorCorePoolSize
                || executorQueueCapacity <= 0) {
            throw new IllegalArgumentException("Invalid notification executor configuration");
        }
        if (emailFrom == null || emailFrom.isBlank()) {
            throw new IllegalArgumentException("Notification email sender address must not be blank");
        }
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
