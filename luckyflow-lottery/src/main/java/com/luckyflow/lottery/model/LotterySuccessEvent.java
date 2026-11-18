package com.luckyflow.lottery.model;

/**
 * 表示中奖记录已经成立的业务事实。
 *
 * <p>事件本身不依赖消息中间件 API，后续可由不同基础设施适配器负责传递。</p>
 *
 * @param winningRecordId 中奖记录标识
 * @param userId 中奖用户标识
 * @param activityId 活动标识
 * @param activityPrizeId 活动奖品标识
 * @param prizeId 奖品标识
 * @param prizeType 奖品类型
 */
public record LotterySuccessEvent(
        Long winningRecordId,
        Long userId,
        Long activityId,
        Long activityPrizeId,
        Long prizeId,
        Integer prizeType        
) {
}
