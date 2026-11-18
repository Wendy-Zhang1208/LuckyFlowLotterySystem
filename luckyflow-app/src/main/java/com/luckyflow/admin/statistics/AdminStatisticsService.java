package com.luckyflow.admin.statistics;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 从已提交 MySQL 事实构建基础运营统计，不将 Redis 预占当作成功业务量。 */
@Service
public class AdminStatisticsService {

    private final AdminStatisticsMapper statisticsMapper;
    private final Clock clock;

    public AdminStatisticsService(AdminStatisticsMapper statisticsMapper, Clock clock) {
        this.statisticsMapper = statisticsMapper;
        this.clock = clock;
    }

    public StatisticsOverview overview(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long activityId
    ) {
        LocalDateTime actualEnd = endTime == null ? LocalDateTime.now(clock) : endTime;
        LocalDateTime actualStart = startTime == null ? actualEnd.minusDays(30) : startTime;
        if (!actualStart.isBefore(actualEnd)) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        if (activityId != null && activityId <= 0) {
            throw new IllegalArgumentException("activityId must be positive");
        }

        StatisticsTotals totals = statisticsMapper.selectTotals(
                actualStart,
                actualEnd,
                activityId
        );
        if (totals == null) {
            totals = new StatisticsTotals();
        }
        List<PrizeTypeWinCount> prizeTypes = statisticsMapper.selectPrizeTypeCounts(
                        actualStart,
                        actualEnd,
                        activityId
                ).stream()
                .map(value -> new PrizeTypeWinCount(
                        prizeTypeName(value.getPrizeType()),
                        value.getWinCount()
                ))
                .toList();
        return new StatisticsOverview(
                actualStart,
                actualEnd,
                activityId == null ? null : Long.toString(activityId),
                totals.getDrawCount(),
                totals.getParticipantCount(),
                totals.getWinCount(),
                ratio(totals.getWinCount(), totals.getDrawCount()),
                totals.getPointsConsumed(),
                prizeTypes,
                totals.getPhysicalWinCount(),
                totals.getClaimedPhysicalWinCount(),
                ratio(totals.getClaimedPhysicalWinCount(), totals.getPhysicalWinCount()),
                LocalDateTime.now(clock)
        );
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private String prizeTypeName(Integer type) {
        if (type == null) {
            return "UNKNOWN";
        }
        return switch (type) {
            case 1 -> "PHYSICAL";
            case 2 -> "VIRTUAL";
            case 3 -> "POINTS";
            default -> "UNKNOWN";
        };
    }

    public record StatisticsOverview(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String activityId,
            long drawCount,
            long participantCount,
            long winCount,
            BigDecimal winRate,
            long pointsConsumed,
            List<PrizeTypeWinCount> prizeTypes,
            long physicalWinCount,
            long claimedPhysicalWinCount,
            BigDecimal claimRate,
            LocalDateTime asOf
    ) {
    }

    public record PrizeTypeWinCount(String prizeType, long winCount) {
    }
}
