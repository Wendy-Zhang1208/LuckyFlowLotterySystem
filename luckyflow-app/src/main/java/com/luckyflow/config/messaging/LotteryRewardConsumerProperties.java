package com.luckyflow.config.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 正式奖励 Consumer 的可调运行参数。 */
@ConfigurationProperties(prefix = "luckyflow.rabbit.reward-consumer")
public class LotteryRewardConsumerProperties {

    private boolean enabled = true;
    private boolean autoStartup = true;
    private int concurrency = 2;
    private int prefetch = 10;
    private long confirmTimeoutMillis = 10_000L;
    private int infrastructureDeliveryLimit = 5;
    private int infrastructureRetryMinMillis = 1_000;
    private int infrastructureRetryMaxMillis = 5_000;
    private int retryOneDelayMillis = 5_000;
    private int retryTwoDelayMillis = 25_000;

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

    public long getConfirmTimeoutMillis() {
        return confirmTimeoutMillis;
    }

    public void setConfirmTimeoutMillis(long confirmTimeoutMillis) {
        this.confirmTimeoutMillis = confirmTimeoutMillis;
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

    public int getRetryOneDelayMillis() {
        return retryOneDelayMillis;
    }

    public void setRetryOneDelayMillis(int retryOneDelayMillis) {
        this.retryOneDelayMillis = retryOneDelayMillis;
    }

    public int getRetryTwoDelayMillis() {
        return retryTwoDelayMillis;
    }

    public void setRetryTwoDelayMillis(int retryTwoDelayMillis) {
        this.retryTwoDelayMillis = retryTwoDelayMillis;
    }

    public void validate() {
        if (concurrency <= 0 || prefetch <= 0 || confirmTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Reward consumer concurrency, prefetch and confirm timeout must be positive");
        }
        if (infrastructureDeliveryLimit <= 0
                || infrastructureRetryMinMillis <= 0
                || infrastructureRetryMaxMillis < infrastructureRetryMinMillis) {
            throw new IllegalArgumentException("Invalid reward consumer infrastructure retry configuration");
        }
        if (retryOneDelayMillis <= 0 || retryTwoDelayMillis <= retryOneDelayMillis) {
            throw new IllegalArgumentException("Invalid reward consumer business retry delays");
        }
    }
}
