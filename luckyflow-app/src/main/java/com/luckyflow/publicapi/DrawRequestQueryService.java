package com.luckyflow.publicapi;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.lottery.entity.DrawRequest;
import com.luckyflow.lottery.model.DrawResult;
import com.luckyflow.lottery.service.Interface.DrawRequestService;
import com.luckyflow.lottery.service.Interface.DrawResourceReservationService;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.luckyflow.exception.ApiErrorCodes.FORBIDDEN;
import static com.luckyflow.exception.ApiErrorCodes.RESOURCE_NOT_FOUND;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.CANCELLED;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.INIT;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.SUCCESS;

/** 面向本人查询的 DrawRequest 只读投影，不触发重新抽奖或恢复动作。 */
@Service
public class DrawRequestQueryService {

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final DrawRequestService drawRequestService;
    private final DrawResourceReservationService reservationService;
    private final DrawEligibilityService eligibilityService;

    public DrawRequestQueryService(
            DrawRequestService drawRequestService,
            DrawResourceReservationService reservationService,
            DrawEligibilityService eligibilityService
    ) {
        this.drawRequestService = drawRequestService;
        this.reservationService = reservationService;
        this.eligibilityService = eligibilityService;
    }

    public DrawRequestResponse findOwnedRequest(
            String requestId,
            long currentUserId,
            long activityId
    ) {
        validateIdentity(requestId, currentUserId, activityId);
        DrawRequest request = drawRequestService.findRequest(requestId);
        if (request == null) {
            throw new BusinessException(RESOURCE_NOT_FOUND, "抽奖请求不存在");
        }
        assertOwnership(request, currentUserId, activityId);
        return toResponse(request, currentUserId);
    }

    public DrawRequestResponse findLatestPending(long currentUserId, long activityId) {
        if (currentUserId <= 0 || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
        DrawRequest request = drawRequestService.findLatestPendingRequest(
                currentUserId,
                activityId
        );
        if (request == null) {
            throw new BusinessException(RESOURCE_NOT_FOUND, "没有待查询的抽奖请求");
        }
        assertOwnership(request, currentUserId, activityId);
        return toResponse(request, currentUserId);
    }

    private DrawRequestResponse toResponse(DrawRequest request, long currentUserId) {
        if (Integer.valueOf(SUCCESS).equals(request.getStatus())) {
            DrawResult result = drawRequestService.readCompletedResult(request);
            return new DrawRequestResponse(
                    request.getRequestId(),
                    "SUCCESS",
                    DrawResultResponse.from(result),
                    null,
                    null
            );
        }
        if (Integer.valueOf(CANCELLED).equals(request.getStatus())) {
            boolean recoveryComplete = reservationService.isCancellationRecoveryComplete(
                    request.getRequestId()
            );
            boolean eligibleToRetry = recoveryComplete
                    && eligibilityService.evaluate(currentUserId, request.getActivityId()).canDraw();
            return new DrawRequestResponse(
                    request.getRequestId(),
                    "CANCELLED",
                    null,
                    recoveryComplete,
                    eligibleToRetry
            );
        }
        if (Integer.valueOf(INIT).equals(request.getStatus())) {
            // 当前内部 INIT 在登记时已经获得 executionToken，外部语义因此是 PROCESSING。
            return new DrawRequestResponse(
                    request.getRequestId(),
                    "PROCESSING",
                    null,
                    null,
                    null
            );
        }
        throw new IllegalStateException("Unsupported draw request status");
    }

    private void assertOwnership(DrawRequest request, long currentUserId, long activityId) {
        if (!Objects.equals(request.getUserId(), currentUserId)
                || !Objects.equals(request.getActivityId(), activityId)) {
            throw new BusinessException(FORBIDDEN, "您无权查询该抽奖请求");
        }
    }

    private void validateIdentity(String requestId, long currentUserId, long activityId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw new IllegalArgumentException("requestId is too long");
        }
        if (currentUserId <= 0 || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
    }

    public record DrawRequestResponse(
            String requestId,
            String status,
            DrawResultResponse result,
            Boolean recoveryComplete,
            Boolean eligibleToRetry
    ) {
    }

    public record DrawResultResponse(
            String drawRecordId,
            boolean won,
            String prizeId,
            String prizeName,
            Integer prizeType
    ) {
        public static DrawResultResponse from(DrawResult result) {
            if (result == null || result.drawRecordId() == null) {
                throw new IllegalStateException("Successful draw request has no committed result");
            }
            return new DrawResultResponse(
                    Long.toString(result.drawRecordId()),
                    result.won(),
                    result.prizeId() == null ? null : Long.toString(result.prizeId()),
                    result.prizeName(),
                    result.prizeType()
            );
        }
    }
}
