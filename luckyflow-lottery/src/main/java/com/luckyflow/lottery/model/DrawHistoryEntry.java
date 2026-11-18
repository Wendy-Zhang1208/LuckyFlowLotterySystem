package com.luckyflow.lottery.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 个人中心使用的已提交抽奖事实投影，不包含 Redis 预占等内部状态。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DrawHistoryEntry {

    private Long drawRecordId;
    private Long activityId;
    private String activityName;
    private Integer resultStatus;
    private Long winningRecordId;
    private Long activityPrizeId;
    private Long prizeId;
    private String prizeName;
    private Integer prizeType;
    private LocalDateTime drawTime;
}
