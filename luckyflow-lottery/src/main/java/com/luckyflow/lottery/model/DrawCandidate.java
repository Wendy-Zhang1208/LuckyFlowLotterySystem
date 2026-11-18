package com.luckyflow.lottery.model;

/**
 * 描述单个候选奖品配置的不可变算法输入对象。
 *
 * <p>该类型有意与持久化注解解耦，只携带抽奖策略所需的数据。</p>
 *
 * @param activityPrizeId 活动奖品配置标识
 * @param prizeId 奖品目录标识
 * @param weight 整数抽奖权重
 * @param remainingInventory 用于资格过滤的剩余库存快照
 */
public record DrawCandidate(
        Long activityPrizeId,
        Long prizeId,
        int weight,
        int remainingInventory
) {
}
