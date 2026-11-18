package com.luckyflow.admin.settings;

import com.luckyflow.notification.config.NotificationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 只投影可安全公开给管理员的运行状态；连接地址、账号、密钥和令牌永远不进入响应。
 */
@Service
public class AdminSettingsService {

    private final NotificationProperties notificationProperties;
    private final boolean emailProviderConfigured;
    private final boolean smsProviderConfigured;
    private final boolean rewardConsumerEnabled;
    private final boolean notificationConsumerEnabled;

    public AdminSettingsService(
            NotificationProperties notificationProperties,
            @Value("${luckyflow.notification.email-provider-configured:false}")
            boolean emailProviderConfigured,
            @Value("${luckyflow.notification.sms-provider-configured:false}")
            boolean smsProviderConfigured,
            @Value("${luckyflow.rabbit.reward-consumer.enabled:true}")
            boolean rewardConsumerEnabled,
            @Value("${luckyflow.rabbit.notification-consumer.enabled:true}")
            boolean notificationConsumerEnabled
    ) {
        this.notificationProperties = notificationProperties;
        this.emailProviderConfigured = emailProviderConfigured;
        this.smsProviderConfigured = smsProviderConfigured;
        this.rewardConsumerEnabled = rewardConsumerEnabled;
        this.notificationConsumerEnabled = notificationConsumerEnabled;
    }

    public SafeSettingsView getSafeSettings() {
        return new SafeSettingsView(
                "Asia/Shanghai",
                notificationProperties.isSchedulingEnabled(),
                providerState(emailProviderConfigured),
                providerState(smsProviderConfigured),
                rewardConsumerEnabled,
                notificationConsumerEnabled
        );
    }

    private String providerState(boolean configured) {
        return configured ? "CONFIGURED" : "UNCONFIGURED";
    }

    public record SafeSettingsView(
            String businessTimeZone,
            boolean notificationSchedulingEnabled,
            String emailProviderStatus,
            String smsProviderStatus,
            boolean rewardConsumerEnabled,
            boolean notificationConsumerEnabled
    ) {
    }
}
