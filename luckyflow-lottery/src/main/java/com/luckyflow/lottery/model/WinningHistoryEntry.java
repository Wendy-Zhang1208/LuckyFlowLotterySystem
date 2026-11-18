package com.luckyflow.lottery.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 个人中心使用的中奖事实投影；只有真实存在 WinningRecord 的记录会进入该列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WinningHistoryEntry {

    private Long winningRecordId;
    private Long drawRecordId;
    private Long activityId;
    private String activityName;
    private Long activityPrizeId;
    private Long prizeId;
    private String prizeName;
    private Integer prizeType;
    private LocalDateTime winTime;
}
