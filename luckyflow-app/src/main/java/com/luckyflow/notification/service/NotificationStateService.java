package com.luckyflow.notification.service;

import java.time.LocalDateTime;

/** 集中维护通知发送结果的原子状态迁移。 */
public interface NotificationStateService {

    /**
     * 外部调用开始前确认当前 attempt 仍有效并刷新超时起点；失效的排队任务必须停止发送。
     */
    boolean markSendingStarted(
            Long notificationId,
            String processingToken,
            int expectedRetryCount,
            LocalDateTime processingTime
    );

    /** 接受可信发送流程的明确成功结果；合法迟到成功不校验旧 attempt token。 */
    boolean markSucceeded(Long notificationId, LocalDateTime sendTime);

    /** 仅当前 PROCESSING attempt 可以登记失败，迟到失败不能覆盖新 attempt 或 SUCCESS。 */
    boolean markFailed(
            Long notificationId,
            String processingToken,
            int expectedRetryCount,
            String lastError,
            LocalDateTime failureTime
    );
}
