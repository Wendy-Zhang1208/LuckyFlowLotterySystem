package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.entity.DrawRequest;
import com.luckyflow.lottery.model.DrawRequestClaim;
import com.luckyflow.lottery.model.DrawResult;

import java.time.LocalDateTime;
import java.util.List;

/** 管理稳定请求标识、执行权和最终抽奖结果。 */
public interface DrawRequestService {

    DrawRequestClaim registerAndClaim(String requestId, Long userId, Long activityId);

    DrawRequest lockForExecution(String requestId, String executionToken);

    void markSuccess(String requestId, String executionToken, DrawResult result);

    /**
     * 在独立短事务中重新锁定请求并按条件取消。
     *
     * @return 数据库重查后的最终状态
     */
    int cancelIfEligible(
            String requestId,
            String expectedExecutionToken,
            LocalDateTime executionCutoff,
            boolean force
    );

    List<String> findRecoverableRequestIds(LocalDateTime executionCutoff, int limit);

    DrawRequest findRequest(String requestId);

    /**
     * 查询本人在指定活动中仍需继续查询或等待补偿的最新请求。
     *
     * <p>INIT 请求始终属于未决；CANCELLED 仅在仍存在非安全终态资源意图时属于未决。</p>
     */
    DrawRequest findLatestPendingRequest(Long userId, Long activityId);

    /** 从 SUCCESS 请求的 MySQL 结果快照读取已经提交的抽奖结果。 */
    DrawResult readCompletedResult(DrawRequest request);
}
