package com.luckyflow.notification.sender;

/** 发送器所需的最小通知快照，不在发送阶段重新读取用户联系方式。 */
public record NotificationSendCommand(
        Long notificationId,
        Long winningRecordId,
        Long userId,
        String recipient
) {

    public NotificationSendCommand {
        requirePositive(notificationId, "notificationId");
        requirePositive(winningRecordId, "winningRecordId");
        requirePositive(userId, "userId");
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Notification recipient must not be blank");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
