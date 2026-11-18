package com.luckyflow.notification.constant;

/** 通知发送渠道；渠道值会持久化到 notification_record，禁止随意调整。 */
public final class NotificationChannelConstants {

    public static final int SMS = 1;

    public static final int EMAIL = 2;

    private NotificationChannelConstants() {
    }

    public static boolean isSupported(int channel) {
        return channel == SMS || channel == EMAIL;
    }
}
