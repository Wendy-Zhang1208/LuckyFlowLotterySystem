package com.luckyflow.admin.statistics;

import lombok.Getter;
import lombok.Setter;

/** 基于 MySQL 已提交业务事实聚合出的统计原始值。 */
@Getter
@Setter
public class StatisticsTotals {

    private long drawCount;
    private long participantCount;
    private long winCount;
    private long pointsConsumed;
    private long physicalWinCount;
    private long claimedPhysicalWinCount;
}
