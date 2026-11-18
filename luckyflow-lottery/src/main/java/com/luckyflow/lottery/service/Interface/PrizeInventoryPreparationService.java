package com.luckyflow.lottery.service.Interface;

import java.util.List;

/**
 * 活动开放前的 Redis 奖池准备服务。
 */
public interface PrizeInventoryPreparationService {

    /** 发布流程的可恢复路径；每条库存写入与 attempt 归属登记原子完成。 */
    void prepareOwned(Long activityId, String attemptToken);

    /** 发布提交状态已由 MySQL 确认后，按归属 token 幂等清理。 */
    void cleanupOwned(Long activityId, String attemptToken, boolean removeInventory);

    /**
     * 将指定活动的全部奖品剩余库存初始化到 Redis。
     *
     * @param activityId 活动 ID，必须为正数
     * @return 本次调用实际初始化成功的活动奖品 ID，供后续流程失败时精确补偿
     * @throws IllegalArgumentException 活动 ID 或奖品库存值不合法时抛出
     * @throws IllegalStateException 奖池为空或必要库存字段缺失时抛出
     */
    List<Long> prepare(Long activityId);

    /**
     * 对指定活动本次已经成功初始化的库存执行 best-effort 补偿。
     *
     * @param activityId 活动 ID
     * @param initializedActivityPrizeIds 本次调用实际初始化成功的活动奖品 ID
     * @param initializationFailure 触发补偿的主异常，补偿异常将附加到该异常
     */
    void compensateInitializedInventories(
        Long activityId,
        List<Long> initializedActivityPrizeIds,
        RuntimeException initializationFailure
    );
}
