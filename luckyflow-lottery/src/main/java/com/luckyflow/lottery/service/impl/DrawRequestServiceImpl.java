package com.luckyflow.lottery.service.impl;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.lottery.entity.DrawRequest;
import com.luckyflow.lottery.mapper.DrawRequestMapper;
import com.luckyflow.lottery.model.DrawRequestClaim;
import com.luckyflow.lottery.model.DrawResult;
import com.luckyflow.lottery.service.Interface.DrawRequestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.CANCELLED;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.INIT;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.SUCCESS;

@Service
public class DrawRequestServiceImpl implements DrawRequestService {

    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final DrawRequestMapper drawRequestMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DrawRequestServiceImpl(
            DrawRequestMapper drawRequestMapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.drawRequestMapper = drawRequestMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public DrawRequestClaim registerAndClaim(String requestId, Long userId, Long activityId) {
        validateRequestIdentity(requestId, userId, activityId);

        LocalDateTime now = LocalDateTime.now(clock);
        String executionToken = UUID.randomUUID().toString();
        DrawRequest request = new DrawRequest();
        request.setRequestId(requestId);
        request.setUserId(userId);
        request.setActivityId(activityId);
        request.setStatus(INIT);
        request.setExecutionToken(executionToken);
        request.setExecutionTime(now);
        request.setCreatedTime(now);
        request.setUpdatedTime(now);

        // 登记先于任何 Redis 预扣独立提交；INSERT IGNORE 避免用异常驱动并发幂等分支。
        if (drawRequestMapper.insertIfAbsent(request) == 1) {
            return DrawRequestClaim.claimed(executionToken);
        }

        DrawRequest existing = drawRequestMapper.selectByRequestIdForUpdate(requestId);
        if (existing == null) {
            throw new IllegalStateException("Draw request disappeared after duplicate registration");
        }
        assertOwnership(existing, userId, activityId);

        if (Integer.valueOf(SUCCESS).equals(existing.getStatus())) {
            return DrawRequestClaim.completed(readResult(existing));
        }
        if (Integer.valueOf(CANCELLED).equals(existing.getStatus())) {
            throw new BusinessException("Draw request has been cancelled");
        }
        throw new BusinessException("Draw request is already being processed");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DrawRequest lockForExecution(String requestId, String executionToken) {
        validateExecution(requestId, executionToken);
        DrawRequest request = drawRequestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null) {
            throw new IllegalStateException("Draw request does not exist");
        }
        if (!Integer.valueOf(INIT).equals(request.getStatus())
                || !executionToken.equals(request.getExecutionToken())) {
            throw new BusinessException("Draw request is no longer executable");
        }
        return request;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void markSuccess(String requestId, String executionToken, DrawResult result) {
        validateExecution(requestId, executionToken);
        Objects.requireNonNull(result, "Draw result must not be null");

        String resultPayload;
        try {
            resultPayload = objectMapper.writeValueAsString(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize draw request result", exception);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (drawRequestMapper.markSuccess(requestId, executionToken, resultPayload, now) != 1) {
            throw new IllegalStateException("Failed to mark draw request as successful");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int cancelIfEligible(
            String requestId,
            String expectedExecutionToken,
            LocalDateTime executionCutoff,
            boolean force
    ) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        DrawRequest request = drawRequestMapper.selectByRequestIdForUpdate(requestId);
        if (request == null) {
            // UNKNOWN 场景查不到请求不能推断主事务失败，更不能据此释放 Redis。
            throw new IllegalStateException("Draw request does not exist during recovery");
        }
        if (!Integer.valueOf(INIT).equals(request.getStatus())) {
            return request.getStatus();
        }
        if (expectedExecutionToken != null
                && !expectedExecutionToken.equals(request.getExecutionToken())) {
            return INIT;
        }
        if (!force && (executionCutoff == null
                || request.getExecutionTime() == null
                || request.getExecutionTime().isAfter(executionCutoff))) {
            return INIT;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int affectedRows = drawRequestMapper.markCancelled(
                requestId,
                request.getExecutionToken(),
                now
        );
        if (affectedRows != 1) {
            throw new IllegalStateException("Failed to cancel draw request");
        }
        return CANCELLED;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<String> findRecoverableRequestIds(LocalDateTime executionCutoff, int limit) {
        Objects.requireNonNull(executionCutoff, "executionCutoff must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return drawRequestMapper.selectRecoverableRequestIdsForUpdate(
                executionCutoff,
                LocalDateTime.now(clock),
                limit
        );
    }

    @Override
    public DrawRequest findRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return drawRequestMapper.selectById(requestId);
    }

    @Override
    public DrawRequest findLatestPendingRequest(Long userId, Long activityId) {
        if (userId == null || userId <= 0 || activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
        return drawRequestMapper.selectLatestPendingByOwnerAndActivity(userId, activityId);
    }

    @Override
    public DrawResult readCompletedResult(DrawRequest request) {
        Objects.requireNonNull(request, "Draw request must not be null");
        if (!Integer.valueOf(SUCCESS).equals(request.getStatus())) {
            throw new IllegalArgumentException("Only a successful draw request has a result");
        }
        return readResult(request);
    }

    private DrawResult readResult(DrawRequest request) {
        if (request.getResultPayload() == null || request.getResultPayload().isBlank()) {
            throw new IllegalStateException("Successful draw request has no result payload");
        }
        try {
            return objectMapper.readValue(request.getResultPayload(), DrawResult.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize draw request result", exception);
        }
    }

    private void assertOwnership(DrawRequest request, Long userId, Long activityId) {
        if (!Objects.equals(request.getUserId(), userId)
                || !Objects.equals(request.getActivityId(), activityId)) {
            throw new BusinessException("Draw request does not belong to this user and activity");
        }
    }

    private void validateRequestIdentity(String requestId, Long userId, Long activityId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (requestId.length() > MAX_REQUEST_ID_LENGTH) {
            throw new IllegalArgumentException("requestId is too long");
        }
        if (userId == null || userId <= 0 || activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
    }

    private void validateExecution(String requestId, String executionToken) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (executionToken == null || executionToken.isBlank()) {
            throw new IllegalArgumentException("executionToken must not be blank");
        }
    }
}
