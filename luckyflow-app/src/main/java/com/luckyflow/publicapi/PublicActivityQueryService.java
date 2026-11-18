package com.luckyflow.publicapi;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.marketing.constant.ActivityThemeKeys;
import com.luckyflow.marketing.entity.Activity;
import com.luckyflow.marketing.entity.ActivityPrize;
import com.luckyflow.marketing.entity.ActivityRule;
import com.luckyflow.marketing.entity.Prize;
import com.luckyflow.marketing.service.ActivityPrizeService;
import com.luckyflow.marketing.service.ActivityRuleService;
import com.luckyflow.marketing.service.ActivityService;
import com.luckyflow.marketing.service.PrizeService;
import com.luckyflow.response.PageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.luckyflow.exception.ApiErrorCodes.RESOURCE_NOT_FOUND;

/** 将 Marketing 内部实体投影成不含权重、内部库存和奖励值的公开白名单 DTO。 */
@Service
public class PublicActivityQueryService {

    private final ActivityService activityService;
    private final ActivityPrizeService activityPrizeService;
    private final PrizeService prizeService;
    private final ActivityRuleService activityRuleService;

    public PublicActivityQueryService(
            ActivityService activityService,
            ActivityPrizeService activityPrizeService,
            PrizeService prizeService,
            ActivityRuleService activityRuleService
    ) {
        this.activityService = activityService;
        this.activityPrizeService = activityPrizeService;
        this.prizeService = prizeService;
        this.activityRuleService = activityRuleService;
    }

    public PageResponse<ActivitySummary> findActivities(
            String status,
            String keyword,
            int page,
            int size
    ) {
        String normalizedStatus = status == null || status.isBlank() ? "ONGOING" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ALL", "ONGOING", "UPCOMING", "ENDED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("Unsupported public activity status");
        }
        ActivityService.PublicActivityPage result =
                activityService.findPublicActivities(normalizedStatus, keyword, page, size);
        return new PageResponse<>(
                result.records().stream().map(this::toSummary).toList(),
                page,
                size,
                result.total()
        );
    }

    public ActivityDetail findDetail(long activityId) {
        Activity activity = requirePublicActivity(activityId);
        List<PrizeSummary> prizes = activityPrizeService.findEnabledByActivityId(activityId)
                .stream()
                .map(this::toPrizeSummary)
                .toList();
        List<RuleSummary> rules = activityRuleService.findByActivityId(activityId)
                .stream()
                .map(this::toRuleSummary)
                .toList();
        return new ActivityDetail(
                Long.toString(activity.getId()),
                activity.getName(),
                safeSummary(activity.getSummary()),
                requireSupportedTheme(activity.getThemeKey()),
                activityService.resolveDisplayStatus(activity),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getDailyDrawLimit(),
                activity.getPointCost(),
                prizes,
                rules
        );
    }

    Activity requirePublicActivity(long activityId) {
        if (activityId <= 0) {
            throw new IllegalArgumentException("activityId must be positive");
        }
        Activity activity = activityService.findPublicActivity(activityId);
        if (activity == null) {
            throw new BusinessException(RESOURCE_NOT_FOUND, "活动不存在或尚未公开");
        }
        return activity;
    }

    private ActivitySummary toSummary(Activity activity) {
        return new ActivitySummary(
                Long.toString(requireId(activity.getId(), "activity")),
                activity.getName(),
                safeSummary(activity.getSummary()),
                requireSupportedTheme(activity.getThemeKey()),
                activityService.resolveDisplayStatus(activity),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getDailyDrawLimit(),
                activity.getPointCost()
        );
    }

    private PrizeSummary toPrizeSummary(ActivityPrize activityPrize) {
        Long prizeId = requireId(activityPrize.getPrizeId(), "prize");
        Prize prize = prizeService.findPrize(prizeId);
        if (prize == null) {
            throw new IllegalStateException("Published activity references a missing prize");
        }
        Integer prizeType = activityPrize.getPrizeTypeSnapshot();
        if (prizeType == null) {
            // 尚未正式发布的公开 PUBLISHED 数据不应发生；保守拒绝泄露可变目录类型。
            throw new IllegalStateException("Published activity prize type is not frozen");
        }
        return new PrizeSummary(
                Long.toString(prizeId),
                prize.getName(),
                prize.getDescription(),
                prizeType,
                prize.getIllustration()
        );
    }

    private RuleSummary toRuleSummary(ActivityRule rule) {
        return new RuleSummary(rule.getTitle(), rule.getContent());
    }

    private long requireId(Long id, String resourceName) {
        if (id == null || id <= 0) {
            throw new IllegalStateException(resourceName + " has no valid id");
        }
        return id;
    }

    private String requireSupportedTheme(String themeKey) {
        if (!ActivityThemeKeys.isSupported(themeKey)) {
            throw new IllegalStateException("Persisted activity themeKey is unsupported");
        }
        return themeKey;
    }

    private String safeSummary(String summary) {
        return summary == null ? "" : summary;
    }

    public record ActivitySummary(
            String id,
            String name,
            String summary,
            String themeKey,
            String displayStatus,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int dailyDrawLimit,
            int pointCost
    ) {
    }

    public record ActivityDetail(
            String id,
            String name,
            String summary,
            String themeKey,
            String displayStatus,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int dailyDrawLimit,
            int pointCost,
            List<PrizeSummary> prizes,
            List<RuleSummary> rules
    ) {
        public ActivityDetail {
            prizes = prizes == null ? List.of() : List.copyOf(prizes);
            rules = rules == null ? List.of() : List.copyOf(rules);
        }
    }

    public record PrizeSummary(
            String id,
            String name,
            String description,
            int prizeType,
            String illustration
    ) {
    }

    public record RuleSummary(String title, String content) {
    }
}
