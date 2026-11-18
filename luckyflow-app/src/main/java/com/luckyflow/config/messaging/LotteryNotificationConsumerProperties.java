package com.luckyflow.config.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 正式中奖通知 Consumer 的可调运行参数。 */
@ConfigurationProperties(prefix = "luckyflow.rabbit.notification-consumer")
public class LotteryNotificationConsumerProperties {

    private boolean enabled = true;
    private boolean autoStartup = true;
    private int concurrency = 2;
    private int prefetch = 10;
    private int infrastructureDeliveryLimit = 5;
    private int infrastructureRetryMinMillis = 1_000;
    private int infrastructureRetryMaxMillis = 5_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getPrefetch() {
        return prefetch;
    }

    public void setPrefetch(int prefetch) {
        this.prefetch = prefetch;
    }

    public int getInfrastructureDeliveryLimit() {
        return infrastructureDeliveryLimit;
    }

    public void setInfrastructureDeliveryLimit(int infrastructureDeliveryLimit) {
        this.infrastructureDeliveryLimit = infrastructureDeliveryLimit;
    }

    public int getInfrastructureRetryMinMillis() {
        return infrastructureRetryMinMillis;
    }

    public void setInfrastructureRetryMinMillis(int infrastructureRetryMinMillis) {
        this.infrastructureRetryMinMillis = infrastructureRetryMinMillis;
    }

    public int getInfrastructureRetryMaxMillis() {
        return infrastructureRetryMaxMillis;
    }

    public void setInfrastructureRetryMaxMillis(int infrastructureRetryMaxMillis) {
        this.infrastructureRetryMaxMillis = infrastructureRetryMaxMillis;
    }

    /** 启动前拒绝无界并发或无效的 RabbitMQ 4.3 延迟重投参数。 */
    public void validate() {
        if (concurrency <= 0 || prefetch <= 0) {
            throw new IllegalArgumentException(
                    "Notification consumer concurrency and prefetch must be positive"
            );
        }
        if (infrastructureDeliveryLimit <= 0
                || infrastructureRetryMinMillis <= 0
                || infrastructureRetryMaxMillis < infrastructureRetryMinMillis) {
            throw new IllegalArgumentException(
                    "Invalid notification consumer infrastructure retry configuration"
            );
        }
    }
}
