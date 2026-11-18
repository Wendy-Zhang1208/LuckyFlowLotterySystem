package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.constant.DailyDrawLimitRedisKeys;
import com.luckyflow.lottery.constant.DrawReservationRedisKeys;
import com.luckyflow.lottery.constant.PrizeInventoryRedisKeys;
import com.luckyflow.lottery.entity.DrawRequest;
import com.luckyflow.lottery.entity.DrawResourceReservation;
import com.luckyflow.lottery.mapper.DrawRequestMapper;
import com.luckyflow.lottery.mapper.DrawResourceReservationMapper;
import com.luckyflow.lottery.service.Interface.DrawResourceReservationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.INIT;
import static com.luckyflow.lottery.constant.DrawResourceReservationStatusConstants.COMPENSATED;
import static com.luckyflow.lottery.constant.DrawResourceReservationStatusConstants.CONFIRMED;
import static com.luckyflow.lottery.constant.DrawResourceReservationStatusConstants.NOT_RESERVED;
import static com.luckyflow.lottery.constant.DrawResourceReservationStatusConstants.PREPARED;
import static com.luckyflow.lottery.constant.DrawResourceReservationStatusConstants.RESERVED;
import static com.luckyflow.lottery.constant.DrawResourceTypeConstants.DAILY_DRAW_LIMIT;
import static com.luckyflow.lottery.constant.DrawResourceTypeConstants.PRIZE_INVENTORY;

@Service
public class DrawResourceReservationServiceImpl implements DrawResourceReservationService {

    private static final int DAILY_ATTEMPT_NO = 1;
    private static final int MAX_LAST_ERROR_LENGTH = 500;
    private static final long MAX_RETRY_DELAY_SECONDS = 60L;

    private final DrawRequestMapper drawRequestMapper;
    private final DrawResourceReservationMapper reservationMapper;
    private final Clock clock;

    public DrawResourceReservationServiceImpl(
            DrawRequestMapper drawRequestMapper,
            DrawResourceReservationMapper reservationMapper,
            Clock clock
    ) {
        this.drawRequestMapper = drawRequestMapper;
        this.reservationMapper = reservationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DrawResourceReservation prepareDailyDrawLimit(
            String requestId,
            String executionToken,
            Long userId,
            Long activityId
    ) {
        if (userId == null || userId <= 0 || activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
        LocalDate businessDate = LocalDate.now(clock);
        return prepare(
                requestId,
                executionToken,
                DAILY_DRAW_LIMIT,
                userId,
                DAILY_ATTEMPT_NO,
                DailyDrawLimitRedisKeys.dailyCountKey(activityId, userId, businessDate)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DrawResourceReservation preparePrizeInventory(
            String requestId,
            String executionToken,
            int attemptNo,
            Long activityPrizeId
    ) {
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        if (activityPrizeId == null || activityPrizeId <= 0) {
            throw new IllegalArgumentException("activityPrizeId must be positive");
        }
        return prepare(
                requestId,
                executionToken,
                PRIZE_INVENTORY,
                activityPrizeId,
                attemptNo,
                PrizeInventoryRedisKeys.inventoryKey(activityPrizeId)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markReserved(Long reservationIntentId) {
        transitionPrepared(reservationIntentId, true);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markNotReserved(Long reservationIntentId) {
        transitionPrepared(reservationIntentId, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void confirmReserved(String requestId) {
        validateRequestId(requestId);
        // 一次成功抽奖至少已预扣每日次数；零行意味着资源状态机已损坏，不能把请求错误提交为 SUCCESS。
        if (reservationMapper.confirmReservedByRequestId(
                requestId,
                LocalDateTime.now(clock)
        ) <= 0) {
            throw new IllegalStateException("Draw request has no reserved resources to confirm");
        }
    }

    @Override
    public List<DrawResourceReservation> findPendingConfirmation(String requestId) {
        validateRequestId(requestId);
        return reservationMapper.selectPendingConfirmation(
                requestId,
                LocalDateTime.now(clock)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markConfirmationCompleted(Long reservationIntentId) {
        validateIntentId(reservationIntentId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (reservationMapper.markConfirmationCompleted(reservationIntentId, now) == 1) {
            return;
        }
        DrawResourceReservation current = reservationMapper.selectById(reservationIntentId);
        if (current != null
                && Integer.valueOf(CONFIRMED).equals(current.getStatus())
                && current.getTerminalizedTime() != null) {
            return;
        }
        throw new IllegalStateException("Reservation confirmation cannot be completed");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordConfirmationFailure(Long reservationIntentId, Throwable failure) {
        validateIntentId(reservationIntentId);
        Objects.requireNonNull(failure, "failure must not be null");
        DrawResourceReservation current = reservationMapper.selectById(reservationIntentId);
        if (current == null) {
            throw new IllegalStateException("Reservation intent does not exist");
        }
        if (!Integer.valueOf(CONFIRMED).equals(current.getStatus())
                || current.getTerminalizedTime() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int retryCount = current.getRetryCount() == null ? 0 : current.getRetryCount();
        if (reservationMapper.markConfirmationFailed(
                reservationIntentId,
                now.plusSeconds(retryDelaySeconds(retryCount)),
                summarizeFailure(failure),
                now
        ) != 1) {
            throw new IllegalStateException("Failed to record reservation confirmation failure");
        }
    }

    @Override
    public List<DrawResourceReservation> findPendingCompensation(String requestId) {
        validateRequestId(requestId);
        return reservationMapper.selectPendingCompensation(requestId, LocalDateTime.now(clock));
    }

    @Override
    public boolean isCancellationRecoveryComplete(String requestId) {
        validateRequestId(requestId);
        return reservationMapper.countUnsafeCancellationStates(requestId) == 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markCompensated(Long reservationIntentId) {
        validateIntentId(reservationIntentId);
        int affectedRows = reservationMapper.markCompensated(
                reservationIntentId,
                LocalDateTime.now(clock)
        );
        if (affectedRows == 1) {
            return;
        }
        DrawResourceReservation current = reservationMapper.selectById(reservationIntentId);
        if (current != null && Integer.valueOf(COMPENSATED).equals(current.getStatus())) {
            return;
        }
        throw new IllegalStateException("Reservation intent cannot be marked compensated");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordCompensationFailure(Long reservationIntentId, Throwable failure) {
        validateIntentId(reservationIntentId);
        Objects.requireNonNull(failure, "failure must not be null");
        DrawResourceReservation current = reservationMapper.selectById(reservationIntentId);
        if (current == null) {
            throw new IllegalStateException("Reservation intent does not exist");
        }
        if (!Integer.valueOf(PREPARED).equals(current.getStatus())
                && !Integer.valueOf(RESERVED).equals(current.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int retryCount = current.getRetryCount() == null ? 0 : current.getRetryCount();
        LocalDateTime nextRetryTime = now.plusSeconds(retryDelaySeconds(retryCount));
        if (reservationMapper.markCompensationFailed(
                reservationIntentId,
                nextRetryTime,
                summarizeFailure(failure),
                now
        ) != 1) {
            throw new IllegalStateException("Failed to record reservation compensation failure");
        }
    }

    private DrawResourceReservation prepare(
            String requestId,
            String executionToken,
            int resourceType,
            Long resourceId,
            int attemptNo,
            String resourceKey
    ) {
        validateRequestId(requestId);
        if (executionToken == null || executionToken.isBlank()) {
            throw new IllegalArgumentException("executionToken must not be blank");
        }

        // 每次意图写入都在短事务内锁定请求并验证 fencing token；事务提交后才执行 Redis。
        // 因而不会出现主业务事务占用连接和请求行锁、REQUIRES_NEW 又反向等待自己的情况。
        DrawRequest request = drawRequestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null
                || !Integer.valueOf(INIT).equals(request.getStatus())
                || !executionToken.equals(request.getExecutionToken())) {
            throw new IllegalStateException("Draw request is no longer executable");
        }

        DrawResourceReservation existing = reservationMapper.findByIntent(
                requestId,
                resourceType,
                attemptNo
        );
        if (existing != null) {
            assertSameIntent(existing, resourceId, resourceKey);
            return existing;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String reservationId = UUID.randomUUID().toString();
        DrawResourceReservation reservation = new DrawResourceReservation();
        reservation.setRequestId(requestId);
        reservation.setResourceType(resourceType);
        reservation.setResourceId(resourceId);
        reservation.setAttemptNo(attemptNo);
        reservation.setResourceKey(resourceKey);
        reservation.setReservationId(reservationId);
        reservation.setReservationKey(DrawReservationRedisKeys.reservationKey(reservationId));
        reservation.setStatus(PREPARED);
        reservation.setRetryCount(0);
        reservation.setNextRetryTime(now);
        reservation.setCreatedTime(now);
        reservation.setUpdatedTime(now);

        if (reservationMapper.insert(reservation) != 1 || reservation.getId() == null) {
            throw new IllegalStateException("Failed to persist Redis reservation intent");
        }
        return reservation;
    }

    private void transitionPrepared(Long reservationIntentId, boolean reserved) {
        validateIntentId(reservationIntentId);
        LocalDateTime now = LocalDateTime.now(clock);
        int affectedRows = reserved
                ? reservationMapper.markReserved(reservationIntentId, now)
                : reservationMapper.markNotReserved(reservationIntentId, now);
        if (affectedRows == 1) {
            return;
        }

        DrawResourceReservation current = reservationMapper.selectById(reservationIntentId);
        int expectedStatus = reserved ? RESERVED : NOT_RESERVED;
        if (current != null && Integer.valueOf(expectedStatus).equals(current.getStatus())) {
            return;
        }
        throw new IllegalStateException("Reservation intent state changed concurrently");
    }

    private void assertSameIntent(
            DrawResourceReservation existing,
            Long resourceId,
            String resourceKey
    ) {
        if (!Objects.equals(existing.getResourceId(), resourceId)
                || !Objects.equals(existing.getResourceKey(), resourceKey)) {
            throw new IllegalStateException("Reservation attempt is already bound to another resource");
        }
    }

    private long retryDelaySeconds(int retryCount) {
        if (retryCount >= 4) {
            return MAX_RETRY_DELAY_SECONDS;
        }
        return Math.min(MAX_RETRY_DELAY_SECONDS, 5L << retryCount);
    }

    private String summarizeFailure(Throwable failure) {
        String message = failure.getClass().getSimpleName() + ": "
                + (failure.getMessage() == null ? "no message" : failure.getMessage());
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ");
        return normalized.length() <= MAX_LAST_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_LAST_ERROR_LENGTH);
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }

    private void validateIntentId(Long reservationIntentId) {
        if (reservationIntentId == null || reservationIntentId <= 0) {
            throw new IllegalArgumentException("reservationIntentId must be positive");
        }
    }
}
