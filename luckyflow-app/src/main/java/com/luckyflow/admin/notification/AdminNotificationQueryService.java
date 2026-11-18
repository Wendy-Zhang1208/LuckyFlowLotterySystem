package com.luckyflow.admin.notification;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.notification.entity.NotificationRecord;
import com.luckyflow.notification.mapper.NotificationRecordMapper;
import com.luckyflow.response.PageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.luckyflow.exception.ApiErrorCodes.RESOURCE_NOT_FOUND;
import static com.luckyflow.notification.constant.NotificationChannelConstants.EMAIL;
import static com.luckyflow.notification.constant.NotificationChannelConstants.SMS;
import static com.luckyflow.notification.constant.NotificationStatusConstants.MANUAL_REVIEW;
import static com.luckyflow.notification.constant.NotificationStatusConstants.PENDING;
import static com.luckyflow.notification.constant.NotificationStatusConstants.PROCESSING;
import static com.luckyflow.notification.constant.NotificationStatusConstants.SUCCESS;

/** 管理端通知只读查询；响应不会暴露原始收件人或 processing token。 */
@Service
public class AdminNotificationQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRecordMapper notificationRecordMapper;

    public AdminNotificationQueryService(NotificationRecordMapper notificationRecordMapper) {
        this.notificationRecordMapper = notificationRecordMapper;
    }

    public PageResponse<NotificationSummary> findPage(
            int page,
            int size,
            String status,
            String channel
    ) {
        validatePage(page, size);
        Integer statusValue = parseStatus(status);
        Integer channelValue = parseChannel(channel);
        long total = notificationRecordMapper.countAdminPage(statusValue, channelValue);
        var records = total == 0
                ? java.util.List.<NotificationRecord>of()
                : notificationRecordMapper.selectAdminPage(
                        statusValue,
                        channelValue,
                        (long) (page - 1) * size,
                        size
                );
        return new PageResponse<>(
                records.stream().map(this::toSummary).toList(),
                page,
                size,
                total
        );
    }

    public NotificationDetail findDetail(long notificationId) {
        if (notificationId <= 0) {
            throw new IllegalArgumentException("notificationId must be positive");
        }
        NotificationRecord record = notificationRecordMapper.selectById(notificationId);
        if (record == null) {
            throw new BusinessException(RESOURCE_NOT_FOUND, "通知记录不存在");
        }
        return new NotificationDetail(
                Long.toString(record.getId()),
                Long.toString(record.getWinningRecordId()),
                Long.toString(record.getUserId()),
                channelName(record.getChannel()),
                maskRecipient(record.getRecipient(), record.getChannel()),
                statusName(record.getStatus()),
                valueOrZero(record.getRetryCount()),
                record.getNextRetryTime(),
                record.getProcessingTime(),
                record.getSendTime(),
                sanitizeError(record.getLastError()),
                isResultKnown(record),
                isRetryScheduled(record),
                record.getCreateTime(),
                record.getUpdateTime()
        );
    }

    private NotificationSummary toSummary(NotificationRecord record) {
        return new NotificationSummary(
                Long.toString(record.getId()),
                Long.toString(record.getWinningRecordId()),
                Long.toString(record.getUserId()),
                channelName(record.getChannel()),
                maskRecipient(record.getRecipient(), record.getChannel()),
                statusName(record.getStatus()),
                valueOrZero(record.getRetryCount()),
                isResultKnown(record),
                isRetryScheduled(record),
                record.getCreateTime(),
                record.getUpdateTime()
        );
    }

    private boolean isResultKnown(NotificationRecord record) {
        return Integer.valueOf(SUCCESS).equals(record.getStatus());
    }

    private boolean isRetryScheduled(NotificationRecord record) {
        return Integer.valueOf(PENDING).equals(record.getStatus())
                && record.getNextRetryTime() != null;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
    }

    private Integer parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toUpperCase()) {
            case "PENDING" -> PENDING;
            case "PROCESSING" -> PROCESSING;
            case "SUCCESS" -> SUCCESS;
            case "MANUAL_REVIEW" -> MANUAL_REVIEW;
            default -> throw new IllegalArgumentException("Unsupported notification status");
        };
    }

    private Integer parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return switch (channel.trim().toUpperCase()) {
            case "SMS" -> SMS;
            case "EMAIL" -> EMAIL;
            default -> throw new IllegalArgumentException("Unsupported notification channel");
        };
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case PENDING -> "PENDING";
            case PROCESSING -> "PROCESSING";
            case SUCCESS -> "SUCCESS";
            case MANUAL_REVIEW -> "MANUAL_REVIEW";
            default -> "UNKNOWN";
        };
    }

    private String channelName(Integer channel) {
        if (Integer.valueOf(SMS).equals(channel)) {
            return "SMS";
        }
        if (Integer.valueOf(EMAIL).equals(channel)) {
            return "EMAIL";
        }
        return "UNKNOWN";
    }

    private String maskRecipient(String recipient, Integer channel) {
        if (recipient == null || recipient.isBlank()) {
            return "";
        }
        if (Integer.valueOf(EMAIL).equals(channel)) {
            int separator = recipient.indexOf('@');
            if (separator <= 0) {
                return "***";
            }
            return recipient.substring(0, 1) + "***" + recipient.substring(separator);
        }
        String digits = recipient.replaceAll("\\s+", "");
        if (digits.length() <= 7) {
            return "***";
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    private String sanitizeError(String lastError) {
        if (lastError == null || lastError.isBlank()) {
            return null;
        }
        String sanitized = lastError
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "***@***")
                .replaceAll("(?<!\\d)1\\d{10}(?!\\d)", "1**********")
                .replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }

    public record NotificationSummary(
            String id,
            String winningRecordId,
            String userId,
            String channel,
            String recipientMasked,
            String status,
            int retryCount,
            boolean resultKnown,
            boolean retryScheduled,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public record NotificationDetail(
            String id,
            String winningRecordId,
            String userId,
            String channel,
            String recipientMasked,
            String status,
            int retryCount,
            LocalDateTime nextRetryTime,
            LocalDateTime processingTime,
            LocalDateTime sendTime,
            String lastError,
            boolean resultKnown,
            boolean retryScheduled,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }
}
