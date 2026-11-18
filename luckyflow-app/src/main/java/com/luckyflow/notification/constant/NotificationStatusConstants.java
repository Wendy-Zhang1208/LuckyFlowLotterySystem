package com.luckyflow.notification.constant;

/** 通知意图状态；SUCCESS 是不可逆的成功终态。 */
public final class NotificationStatusConstants {

    public static final int PENDING = 0;

    public static final int PROCESSING = 1;

    public static final int SUCCESS = 2;

    public static final int MANUAL_REVIEW = 3;

    private NotificationStatusConstants() {
    }
}
