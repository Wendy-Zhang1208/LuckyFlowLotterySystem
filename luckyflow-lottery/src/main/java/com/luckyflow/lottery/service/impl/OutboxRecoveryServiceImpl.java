package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.entity.OutboxEvent;
import com.luckyflow.lottery.mapper.OutboxEventMapper;
import com.luckyflow.lottery.service.Interface.OutboxRecoveryService;
import com.luckyflow.lottery.service.Interface.OutboxStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 使用数据库当前 attempt 信息恢复 Confirm 结果长期未知的 Outbox 事件。 */
@Service
public class OutboxRecoveryServiceImpl implements OutboxRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRecoveryServiceImpl.class);
    private static final String CONFIRM_TIMEOUT_ERROR = "publish confirm timeout";

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxStateService outboxStateService;
    private final Clock clock;

    public OutboxRecoveryServiceImpl(
            OutboxEventMapper outboxEventMapper,
            OutboxStateService outboxStateService,
            Clock clock
    ) {
        this.outboxEventMapper = outboxEventMapper;
        this.outboxStateService = outboxStateService;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverTimedOut(Duration processingTimeout, int batchSize) {
        if (processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("processingTimeout must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(processingTimeout);
        List<OutboxEvent> timedOutEvents =
                outboxEventMapper.selectTimedOutProcessingForUpdate(cutoff, batchSize);
        LOGGER.debug(
                "Outbox recovery query completed, now={}, cutoff={}, processingTimeout={}, candidates={}",
                now,
                cutoff,
                processingTimeout,
                timedOutEvents == null ? null : timedOutEvents.size()
        );
        if (timedOutEvents == null || timedOutEvents.isEmpty()) {
            return 0;
        }

        // 扫描行锁与恢复更新共用当前事务；事务中不执行 RabbitMQ 或其他网络 I/O。
        for (OutboxEvent event : timedOutEvents) {
            validateScannedEvent(event);
            boolean recovered = outboxStateService.markPublishFailed(
                    event.getId(),
                    event.getProcessingToken(),
                    event.getRetryCount(),
                    CONFIRM_TIMEOUT_ERROR
            );
            if (!recovered) {
                // 已锁定行使用数据库当前 token 仍无法更新，说明状态机不变量已被破坏。
                throw new IllegalStateException(
                        "Failed to recover timed-out outbox event, id=" + event.getId()
                );
            }
        }

        return timedOutEvents.size();
    }

    private void validateScannedEvent(OutboxEvent event) {
        if (event == null || event.getId() == null || event.getId() <= 0) {
            throw new IllegalStateException("Timed-out outbox event has invalid id");
        }
        if (event.getProcessingToken() == null || event.getProcessingToken().isBlank()) {
            throw new IllegalStateException(
                    "Timed-out outbox event has invalid processingToken, id=" + event.getId()
            );
        }
        if (event.getRetryCount() == null || event.getRetryCount() < 0) {
            throw new IllegalStateException(
                    "Timed-out outbox event has invalid retryCount, id=" + event.getId()
            );
        }
    }
}
