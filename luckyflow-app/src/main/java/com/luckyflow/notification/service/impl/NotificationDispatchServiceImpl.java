package com.luckyflow.notification.service.impl;

import com.luckyflow.notification.entity.NotificationRecord;
import com.luckyflow.notification.service.NotificationClaimService;
import com.luckyflow.notification.service.NotificationDispatchService;
import com.luckyflow.notification.service.NotificationSendService;
import com.luckyflow.notification.service.NotificationStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/** 先完成数据库 claim，再把网络调用交给独立有界线程池。 */
@Service
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchServiceImpl.class);
    private static final String EXECUTOR_REJECTED_ERROR = "Notification send executor rejected task";

    private final NotificationClaimService notificationClaimService;
    private final NotificationSendService notificationSendService;
    private final NotificationStateService notificationStateService;
    private final Executor notificationSendExecutor;
    private final Clock clock;

    public NotificationDispatchServiceImpl(
            NotificationClaimService notificationClaimService,
            NotificationSendService notificationSendService,
            NotificationStateService notificationStateService,
            @Qualifier("notificationSendExecutor") Executor notificationSendExecutor,
            Clock clock
    ) {
        this.notificationClaimService = notificationClaimService;
        this.notificationSendService = notificationSendService;
        this.notificationStateService = notificationStateService;
        this.notificationSendExecutor = notificationSendExecutor;
        this.clock = clock;
    }

    /** claim 方法返回时行锁已释放，后续线程只执行外部发送及独立状态更新。 */
    @Override
    public int dispatchBatch(int batchSize) {
        List<NotificationRecord> records = notificationClaimService.claimBatch(batchSize);
        for (NotificationRecord record : records) {
            try {
                notificationSendExecutor.execute(() -> notificationSendService.send(record));
            } catch (TaskRejectedException rejected) {
                handleRejectedTask(record, rejected);
            }
        }
        return records.size();
    }

    private void handleRejectedTask(NotificationRecord record, TaskRejectedException rejected) {
        try {
            notificationStateService.markFailed(
                    record.getId(),
                    record.getProcessingToken(),
                    record.getRetryCount(),
                    EXECUTOR_REJECTED_ERROR,
                    LocalDateTime.now(clock)
            );
        } catch (RuntimeException stateFailure) {
            rejected.addSuppressed(stateFailure);
            LOGGER.error(
                    "Failed to persist rejected notification task, id={}",
                    record.getId(),
                    rejected
            );
        }
    }
}
