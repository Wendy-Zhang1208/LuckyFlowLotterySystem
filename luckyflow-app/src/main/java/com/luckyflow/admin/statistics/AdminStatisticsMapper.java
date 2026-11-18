package com.luckyflow.admin.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 管理报表读模型，可跨领域事实表只读聚合，但不承担任何业务写入。 */
@Mapper
public interface AdminStatisticsMapper {

    StatisticsTotals selectTotals(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("activityId") Long activityId
    );

    List<PrizeTypeCount> selectPrizeTypeCounts(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("activityId") Long activityId
    );
}
