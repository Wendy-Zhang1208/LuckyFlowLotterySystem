package com.luckyflow.publicapi;

import com.luckyflow.lottery.entity.DrawRequest;
import com.luckyflow.lottery.service.Interface.DailyDrawLimitService;
import com.luckyflow.lottery.service.Interface.DrawRequestService;
import com.luckyflow.marketing.entity.Activity;
import com.luckyflow.marketing.service.ActivityService;
import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.service.PointAccountService;
import org.springframework.stereotype.Service;

import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.CANCELLED;
import static com.luckyflow.lottery.constant.DrawRequestStatusConstants.INIT;

/** 聚合活动状态、积分、Redis 日次数和未决恢复状态的服务器侧参与资格。 */
@Service
public class DrawEligibilityService {

    public static final String ELIGIBLE = "ELIGIBLE";
    public static final String ACTIVITY_NOT_RUNNING = "ACTIVITY_NOT_RUNNING";
    public static final String DRAW_REQUEST_PROCESSING = "DRAW_REQUEST_PROCESSING";
    public static final String DRAW_REQUEST_RECOVERING = "DRAW_REQUEST_RECOVERING";
    public static final String POINT_ACCOUNT_UNAVAILABLE = "POINT_ACCOUNT_UNAVAILABLE";
    public static final String DAILY_LIMIT_REACHED = "DAILY_LIMIT_REACHED";
    public static final String INSUFFICIENT_POINTS = "INSUFFICIENT_POINTS";

    private final PublicActivityQueryService publicActivityQueryService;
    private final ActivityService activityService;
    private final PointAccountService pointAccountService;
    private final DailyDrawLimitService dailyDrawLimitService;
    private final DrawRequestService drawRequestService;

    public DrawEligibilityService(
            PublicActivityQueryService publicActivityQueryService,
            ActivityService activityService,
            PointAccountService pointAccountService,
            DailyDrawLimitService dailyDrawLimitService,
            DrawRequestService drawRequestService
    ) {
        this.publicActivityQueryService = publicActivityQueryService;
        this.activityService = activityService;
        this.pointAccountService = pointAccountService;
        this.dailyDrawLimitService = dailyDrawLimitService;
        this.drawRequestService = drawRequestService;
    }

    public Eligibility evaluate(long userId, long activityId) {
        if (userId <= 0 || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
        Activity activity = publicActivityQueryService.requirePublicActivity(activityId);
        int dailyLimit = requireNonNegative(activity.getDailyDrawLimit(), "dailyDrawLimit");
        int usedCount = dailyDrawLimitService.getUsedCount(userId, activityId);
        int remainingDraws = Math.max(0, dailyLimit - usedCount);

        PointAccount pointAccount = pointAccountService.findAccountByUserId(userId);
        long pointBalance = pointAccount == null || pointAccount.getBalance() == null
                ? 0L
                : pointAccount.getBalance();
        DrawRequest pendingRequest = drawRequestService.findLatestPendingRequest(userId, activityId);

        String reason = resolveReason(
                activity,
                pointAccount,
                pointBalance,
                remainingDraws,
                pendingRequest
        );
        return new Eligibility(pointBalance, remainingDraws, ELIGIBLE.equals(reason), reason);
    }

    private String resolveReason(
            Activity activity,
            PointAccount pointAccount,
            long pointBalance,
            int remainingDraws,
            DrawRequest pendingRequest
    ) {
        if (!activityService.isCurrentlyDrawable(activity)) {
            return ACTIVITY_NOT_RUNNING;
        }
        if (pendingRequest != null) {
            if (Integer.valueOf(INIT).equals(pendingRequest.getStatus())) {
                return DRAW_REQUEST_PROCESSING;
            }
            if (Integer.valueOf(CANCELLED).equals(pendingRequest.getStatus())) {
                return DRAW_REQUEST_RECOVERING;
            }
            // 查询 SQL 不应返回其他状态；出现时保守阻止，避免产生第二个业务意图。
            return DRAW_REQUEST_RECOVERING;
        }
        if (pointAccount == null || pointAccount.getBalance() == null) {
            return POINT_ACCOUNT_UNAVAILABLE;
        }
        if (remainingDraws <= 0) {
            return DAILY_LIMIT_REACHED;
        }
        int pointCost = requireNonNegative(activity.getPointCost(), "pointCost");
        if (pointBalance < pointCost) {
            return INSUFFICIENT_POINTS;
        }
        return ELIGIBLE;
    }

    private int requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
        return value;
    }

    public record Eligibility(
            long pointBalance,
            int remainingDraws,
            boolean canDraw,
            String reason
    ) {
    }
}
