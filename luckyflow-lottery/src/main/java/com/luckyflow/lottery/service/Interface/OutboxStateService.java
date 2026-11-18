package com.luckyflow.lottery.service.Interface;

/** 负责解释并执行单次 Outbox publish attempt 的数据库状态迁移。 */
public interface OutboxStateService {

    /**
     * 将当前 attempt 标记为已发布。
     *
     * @return {@code true} 表示迁移成功，{@code false} 表示 token 已过期或状态已被推进
     */
    boolean markPublished(Long outboxId, String processingToken, int retryCount);

    /**
     * 将当前 attempt 回退为待处理，并根据当前重试次数计算下一次执行时间。
     *
     * @param retryCount 当前 attempt 开始时的重试次数
     * @return {@code true} 表示迁移成功，{@code false} 表示 stale attempt
     */
    boolean markPublishFailed(
            Long outboxId,
            String processingToken,
            int retryCount,
            String lastError
    );
}
