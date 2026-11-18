package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.entity.DrawResourceReservation;

import java.util.List;

/** 持久化并推进抽奖所需 Redis 资源预扣意图。 */
public interface DrawResourceReservationService {

    DrawResourceReservation prepareDailyDrawLimit(
            String requestId,
            String executionToken,
            Long userId,
            Long activityId
    );

    DrawResourceReservation preparePrizeInventory(
            String requestId,
            String executionToken,
            int attemptNo,
            Long activityPrizeId
    );

    void markReserved(Long reservationIntentId);

    void markNotReserved(Long reservationIntentId);

    void confirmReserved(String requestId);

    List<DrawResourceReservation> findPendingConfirmation(String requestId);

    void markConfirmationCompleted(Long reservationIntentId);

    void recordConfirmationFailure(Long reservationIntentId, Throwable failure);

    List<DrawResourceReservation> findPendingCompensation(String requestId);

    /**
     * 判断已取消请求的全部资源意图是否都进入 NOT_RESERVED 或 COMPENSATED 安全终态。
     * 该判断不受 nextRetryTime 和 Redis 在线状态影响。
     */
    boolean isCancellationRecoveryComplete(String requestId);

    void markCompensated(Long reservationIntentId);

    void recordCompensationFailure(Long reservationIntentId, Throwable failure);
}
