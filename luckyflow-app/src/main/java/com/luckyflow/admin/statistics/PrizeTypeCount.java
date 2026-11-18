package com.luckyflow.admin.statistics;

import lombok.Getter;
import lombok.Setter;

/** 按活动发布时冻结的奖品类型统计中奖次数。 */
@Getter
@Setter
public class PrizeTypeCount {

    private Integer prizeType;
    private long winCount;
}
