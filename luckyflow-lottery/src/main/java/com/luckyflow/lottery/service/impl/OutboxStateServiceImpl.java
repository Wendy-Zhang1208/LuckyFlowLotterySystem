package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.mapper.OutboxEventMapper;
import com.luckyflow.lottery.service.Interface.OutboxStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Outbox publish attempt 状态迁移实现。 */
@Service
public class OutboxStateServiceImpl implements OutboxStateService {

    private static final int MAX_LAST_ERROR_LENGTH = 500;
    private static final long MAX_RETRY_DELAY_SECONDS = 60L;
    private static final String DEFAULT_FAILURE_DESCRIPTION =
            "publisher confirm nack without cause";

    private final OutboxEventMapper outboxEventMapper;
    private final Clock clock;

    public OutboxStateServiceImpl(OutboxEventMapper outboxEventMapper, Clock clock) {
        this.outboxEventMapper = outboxEventMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPublished(Long outboxId, String processingToken, int retryCount) {
        validateAttempt(outboxId, processingToken, retryCount);
        LocalDateTime publishTime = LocalDateTime.now(clock);
        int affectedRows = outboxEventMapper.markPublished(
                outboxId,
                processingToken,
                retryCount,
                publishTime
        );
        return interpretPublisherResult("mark published", outboxId, affectedRows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPublishFailed(
            Long outboxId,
            String processingToken,
            int retryCount,
            String lastError
    ) {
        validateAttempt(outboxId, processingToken, retryCount);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime nextRetryTime = now.plusSeconds(calculateRetryDelaySeconds(retryCount));
        int affectedRows = outboxEventMapper.markPublishFailed(
                outboxId,
                processingToken,
                retryCount,
                nextRetryTime,
                summarizeError(lastError),
                now
        );
        return interpretPublisherResult("mark publish failed", outboxId, affectedRows);
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        // 当前重试次数达到 4 时，本次失败后的 newRetryCount 至少为 5，直接进入 60 秒封顶区间。
        if (retryCount >= 4) {
            return MAX_RETRY_DELAY_SECONDS;
        }
        int newRetryCount = retryCount + 1;
        return 5L * (1L << (newRetryCount - 1));
    }

    private String summarizeError(String lastError) {
        String summary = lastError == null || lastError.isBlank()
                ? DEFAULT_FAILURE_DESCRIPTION
                : lastError.strip().replaceAll("[\\r\\n\\t]+", " ");
        int codePointCount = summary.codePointCount(0, summary.length());
        if (codePointCount <= MAX_LAST_ERROR_LENGTH) {
            return summary;
        }
        int endIndex = summary.offsetByCodePoints(0, MAX_LAST_ERROR_LENGTH);
        return summary.substring(0, endIndex);
    }

    private boolean interpretPublisherResult(String operation, Long outboxId, int affectedRows) {
        if (affectedRows == 1) {
            return true;
        }
        if (affectedRows == 0) {
            // 旧 ACK/NACK 或 Return 回调属于 stale attempt，不得覆盖 Recovery 后的新 token。
            return false;
        }
        throw new IllegalStateException(
                "Unexpected affected rows while attempting to " + operation
                        + ", outboxId=" + outboxId
                        + ", affectedRows=" + affectedRows
        );
    }

    private void validateAttempt(Long outboxId, String processingToken, int retryCount) {
        if (outboxId == null || outboxId <= 0) {
            throw new IllegalArgumentException("Invalid outboxId");
        }
        if (processingToken == null || processingToken.isBlank()) {
            throw new IllegalArgumentException("Invalid processingToken");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
