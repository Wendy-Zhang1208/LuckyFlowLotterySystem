package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.entity.DrawResourceReservation;
import com.luckyflow.lottery.model.DailyDrawReservation;
import com.luckyflow.lottery.model.PrizeInventoryReservation;
import com.luckyflow.lottery.service.Interface.DailyDrawLimitService;
import com.luckyflow.lottery.service.Interface.DrawRequestRecoveryService;
import com.luckyflow.lottery.service.Interface.DrawRequestService;
import com.luckyflow.lottery.service.Interface.DrawResourceReservationService;
import com.luckyflow.lottery.service.Interface.PrizeInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.CANCELLED;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.SUCCESS;
import static com.luckyflow.lottery.constant.DrawResourceTypeConstants.DAILY_DRAW_LIMIT;
import static com.luckyflow.lottery.constant.DrawResourceTypeConstants.PRIZE_INVENTORY;

@Service
public class DrawRequestRecoveryServiceImpl implements DrawRequestRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(DrawRequestRecoveryServiceImpl.class);

    private final DrawRequestService drawRequestService;
    private final DrawResourceReservationService reservationService;
    private final DailyDrawLimitService dailyDrawLimitService;
    private final PrizeInventoryService prizeInventoryService;
    private final Clock clock;
    private final Duration requestTimeout;

    public DrawRequestRecoveryServiceImpl(
            DrawRequestService drawRequestService,
            DrawResourceReservationService reservationService,
            DailyDrawLimitService dailyDrawLimitService,
            PrizeInventoryService prizeInventoryService,
            Clock clock,
            @Value("${luckyflow.lottery.recovery.request-timeout-seconds:120}")
            long requestTimeoutSeconds
    ) {
        if (requestTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Recovery request timeout must be positive");
        }
        this.drawRequestService = drawRequestService;
        this.reservationService = reservationService;
        this.dailyDrawLimitService = dailyDrawLimitService;
        this.prizeInventoryService = prizeInventoryService;
        this.clock = clock;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    }

    @Override
    public void handleTransactionCompletion(
            String requestId,
            String executionToken,
            int completionStatus
    ) {
        try {
            if (completionStatus == TransactionSynchronization.STATUS_COMMITTED) {
                // 数据库 SUCCESS 已经提交后，才把 Redis marker 推进为不可释放的成功终态。
                // 此步骤失败不会反向改变业务事务，由持久化 CONFIRMED 意图继续重试。
                confirmSuccessfulReservations(requestId);
                return;
            }
            if (completionStatus == TransactionSynchronization.STATUS_ROLLED_BACK) {
                cancelAndRecover(requestId, executionToken);
                return;
            }

            if (completionStatus == TransactionSynchronization.STATUS_UNKNOWN) {
                // UNKNOWN 绝不直接释放 Redis。新事务必须先锁定并重查请求；只有确认为 INIT
                // 且成功持久化为 CANCELLED 后，才允许按意图执行补偿。
                verifyUnknownOutcomeAndRecover(requestId, executionToken);
                return;
            }

            log.warn("Ignoring unsupported draw transaction completion status: requestId={}, status={}",
                    requestId, completionStatus);
        } catch (RuntimeException exception) {
            // 回调失败不能覆盖原事务结果；持久化请求状态和意图会由定时恢复再次发现。
            log.error("Draw request completion recovery failed: requestId={}", requestId, exception);
        }
    }

    @Override
    public void cancelAndRecover(String requestId, String executionToken) {
        int status = drawRequestService.cancelIfEligible(
                requestId,
                executionToken,
                LocalDateTime.now(clock),
                true
        );
        if (status == CANCELLED) {
            compensatePendingReservations(requestId);
        } else if (status == SUCCESS) {
            confirmSuccessfulReservations(requestId);
        }
    }

    @Override
    public int recoverBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(requestTimeout);
        List<String> requestIds = drawRequestService.findRecoverableRequestIds(cutoff, batchSize);
        int processed = 0;

        for (String requestId : requestIds) {
            try {
                int status = drawRequestService.cancelIfEligible(
                        requestId,
                        null,
                        cutoff,
                        false
                );
                if (status == CANCELLED) {
                    compensatePendingReservations(requestId);
                    processed++;
                } else if (status == SUCCESS) {
                    confirmSuccessfulReservations(requestId);
                    processed++;
                }
            } catch (RuntimeException exception) {
                log.error("Scheduled draw recovery failed: requestId={}", requestId, exception);
            }
        }
        return processed;
    }

    private void verifyUnknownOutcomeAndRecover(String requestId, String executionToken) {
        int status = drawRequestService.cancelIfEligible(
                requestId,
                executionToken,
                LocalDateTime.now(clock),
                true
        );
        if (status == SUCCESS) {
            confirmSuccessfulReservations(requestId);
            return;
        }
        if (status == CANCELLED) {
            compensatePendingReservations(requestId);
        }
    }

    private void confirmSuccessfulReservations(String requestId) {
        for (DrawResourceReservation intent
                : reservationService.findPendingConfirmation(requestId)) {
            try {
                confirm(intent);
                reservationService.markConfirmationCompleted(intent.getId());
            } catch (RuntimeException confirmationFailure) {
                log.error(
                        "Redis reservation success finalization failed: requestId={}, intentId={}",
                        requestId,
                        intent.getId(),
                        confirmationFailure
                );
                try {
                    reservationService.recordConfirmationFailure(
                            intent.getId(),
                            confirmationFailure
                    );
                } catch (RuntimeException persistenceFailure) {
                    confirmationFailure.addSuppressed(persistenceFailure);
                    log.error(
                            "Failed to persist reservation finalization retry state: requestId={}, intentId={}",
                            requestId,
                            intent.getId(),
                            persistenceFailure
                    );
                }
            }
        }
    }

    private void compensatePendingReservations(String requestId) {
        for (DrawResourceReservation intent
                : reservationService.findPendingCompensation(requestId)) {
            try {
                release(intent);
                reservationService.markCompensated(intent.getId());
            } catch (RuntimeException compensationFailure) {
                log.error(
                        "Redis reservation compensation failed: requestId={}, intentId={}",
                        requestId,
                        intent.getId(),
                        compensationFailure
                );
                try {
                    reservationService.recordCompensationFailure(
                            intent.getId(),
                            compensationFailure
                    );
                } catch (RuntimeException persistenceFailure) {
                    compensationFailure.addSuppressed(persistenceFailure);
                    log.error(
                            "Failed to persist compensation retry state: requestId={}, intentId={}",
                            requestId,
                            intent.getId(),
                            persistenceFailure
                    );
                }
            }
        }
    }

    private void release(DrawResourceReservation intent) {
        if (Integer.valueOf(DAILY_DRAW_LIMIT).equals(intent.getResourceType())) {
            dailyDrawLimitService.release(new DailyDrawReservation(
                    intent.getResourceKey(),
                    intent.getReservationKey()
            ));
            return;
        }
        if (Integer.valueOf(PRIZE_INVENTORY).equals(intent.getResourceType())) {
            prizeInventoryService.release(new PrizeInventoryReservation(
                    intent.getResourceKey(),
                    intent.getReservationKey()
            ));
            return;
        }
        throw new IllegalStateException(
                "Unsupported reservation resource type: " + intent.getResourceType()
        );
    }

    private void confirm(DrawResourceReservation intent) {
        if (Integer.valueOf(DAILY_DRAW_LIMIT).equals(intent.getResourceType())) {
            dailyDrawLimitService.confirm(new DailyDrawReservation(
                    intent.getResourceKey(),
                    intent.getReservationKey()
            ));
            return;
        }
        if (Integer.valueOf(PRIZE_INVENTORY).equals(intent.getResourceType())) {
            prizeInventoryService.confirm(new PrizeInventoryReservation(
                    intent.getResourceKey(),
                    intent.getReservationKey()
            ));
            return;
        }
        throw new IllegalStateException(
                "Unsupported reservation resource type: " + intent.getResourceType()
        );
    }
}
