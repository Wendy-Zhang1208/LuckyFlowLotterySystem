package com.luckyflow.lottery.model;

import java.math.BigDecimal;

/** 数据库中已经成立的中奖事实投影，用于消费侧核对消息内容。 */
public record WinningFact(
        Long winningRecordId,
        Long userId,
        Long activityId,
        Long activityPrizeId,
        Long prizeId,
        Integer prizeTypeSnapshot,
        BigDecimal prizeValueSnapshot,
        Integer resultStatus
) {
}
