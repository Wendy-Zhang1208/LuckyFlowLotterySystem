package com.luckyflow.controller.admin;

import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.marketing.entity.Activity;
import com.luckyflow.marketing.entity.ActivityPrize;
import com.luckyflow.marketing.entity.ActivityRule;
import com.luckyflow.marketing.service.ActivityPrizeService;
import com.luckyflow.marketing.service.ActivityRuleService;
import com.luckyflow.marketing.service.ActivityService;
import com.luckyflow.publicapi.HttpIdParser;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import com.luckyflow.service.ActivityOpeningApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_DRAFT;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_ENDED;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_PENDING_REVIEW;

/** 运营活动五步配置、奖池编辑和正式发布入口。 */
@RestController
@RequestMapping("/api/admin/activities")
public class AdminActivityController {

    private final ActivityService activityService;
    private final ActivityRuleService activityRuleService;
    private final ActivityPrizeService activityPrizeService;
    private final ActivityOpeningApplicationService openingService;

    public AdminActivityController(
            ActivityService activityService,
            ActivityRuleService activityRuleService,
            ActivityPrizeService activityPrizeService,
            ActivityOpeningApplicationService openingService
    ) {
        this.activityService = activityService;
        this.activityRuleService = activityRuleService;
        this.activityPrizeService = activityPrizeService;
        this.openingService = openingService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ActivityView>> findActivities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ActivityService.ActivityPage result = activityService.findActivities(
                keyword,
                status,
                page,
                size
        );
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(ActivityView::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/{activityId}")
    public ApiResponse<ActivityDetail> findActivity(@PathVariable String activityId) {
        long id = parseId(activityId, "activityId");
        Activity activity = requireActivity(id);
        return ApiResponse.success(new ActivityDetail(
                ActivityView.from(activity),
                activityRuleService.findByActivityId(id).stream().map(RuleView::from).toList(),
                activityPrizeService.findByActivityId(id).stream().map(ActivityPrizeView::from).toList()
        ));
    }

    @PostMapping
    public ApiResponse<ActivityView> createDraft(@RequestBody ActivityConfiguration request) {
        return ApiResponse.success(ActivityView.from(activityService.createDraft(request.toEntity(null))));
    }

    @PatchMapping("/{activityId}")
    public ApiResponse<ActivityView> updateConfiguration(
            @PathVariable String activityId,
            @RequestBody ActivityConfiguration request
    ) {
        long id = parseId(activityId, "activityId");
        Activity entity = request.toEntity(id);
        activityService.updateActivityConfiguration(entity);
        return ApiResponse.success(ActivityView.from(requireActivity(id)));
    }

    @PutMapping("/{activityId}/rules")
    public ApiResponse<List<RuleView>> replaceRules(
            @PathVariable String activityId,
            @RequestBody List<RuleInput> rules
    ) {
        long id = parseId(activityId, "activityId");
        List<ActivityRule> input = rules == null
                ? List.of()
                : rules.stream().map(RuleInput::toEntity).toList();
        return ApiResponse.success(
                activityRuleService.replaceRules(id, input).stream().map(RuleView::from).toList()
        );
    }

    @PostMapping("/{activityId}/submit-review")
    public ApiResponse<Void> submitForReview(@PathVariable String activityId) {
        long id = parseId(activityId, "activityId");
        activityService.updateActivityStatus(id, ACTIVITY_STATUS_DRAFT, ACTIVITY_STATUS_PENDING_REVIEW);
        return ApiResponse.success(null);
    }

    @PostMapping("/{activityId}/publish")
    public ApiResponse<Void> publish(@PathVariable String activityId) {
        openingService.publishActivity(parseId(activityId, "activityId"));
        return ApiResponse.success(null);
    }

    @PostMapping("/{activityId}/end")
    public ApiResponse<Void> end(@PathVariable String activityId) {
        long id = parseId(activityId, "activityId");
        Activity activity = requireActivity(id);
        activityService.updateActivityStatus(id, activity.getStatus(), ACTIVITY_STATUS_ENDED);
        return ApiResponse.success(null);
    }

    @GetMapping("/{activityId}/prizes")
    public ApiResponse<List<ActivityPrizeView>> findPool(@PathVariable String activityId) {
        long id = parseId(activityId, "activityId");
        requireActivity(id);
        return ApiResponse.success(
                activityPrizeService.findByActivityId(id).stream().map(ActivityPrizeView::from).toList()
        );
    }

    @PostMapping("/{activityId}/prizes")
    public ApiResponse<ActivityPrizeView> addPoolItem(
            @PathVariable String activityId,
            @RequestBody ActivityPrizeInput request
    ) {
        long id = parseId(activityId, "activityId");
        ActivityPrize created = activityPrizeService.createActivityPrize(request.toEntity(id, null));
        return ApiResponse.success(ActivityPrizeView.from(created));
    }

    @PutMapping("/{activityId}/prizes/{activityPrizeId}")
    public ApiResponse<ActivityPrizeView> updatePoolItem(
            @PathVariable String activityId,
            @PathVariable String activityPrizeId,
            @RequestBody ActivityPrizeInput request
    ) {
        long parsedActivityId = parseId(activityId, "activityId");
        long parsedActivityPrizeId = parseId(activityPrizeId, "activityPrizeId");
        ActivityPrize entity = request.toEntity(parsedActivityId, parsedActivityPrizeId);
        activityPrizeService.updateActivityPrizeConfiguration(parsedActivityId, entity);
        ActivityPrize updated = activityPrizeService.findActivityPrize(parsedActivityPrizeId);
        return ApiResponse.success(ActivityPrizeView.from(updated));
    }

    @DeleteMapping("/{activityId}/prizes/{activityPrizeId}")
    public ApiResponse<Void> deletePoolItem(
            @PathVariable String activityId,
            @PathVariable String activityPrizeId
    ) {
        activityPrizeService.deleteActivityPrize(
                parseId(activityId, "activityId"),
                parseId(activityPrizeId, "activityPrizeId")
        );
        return ApiResponse.success(null);
    }

    private Activity requireActivity(long activityId) {
        Activity activity = activityService.findActivity(activityId);
        if (activity == null) {
            throw new BusinessException(ApiErrorCodes.RESOURCE_NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private static long parseId(String value, String field) {
        return HttpIdParser.requirePositiveLong(value, field);
    }

    public record ActivityConfiguration(
            String name,
            String summary,
            String themeKey,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer dailyDrawLimit,
            Integer pointCost,
            Integer noWinWeight
    ) {
        Activity toEntity(Long id) {
            Activity activity = new Activity();
            activity.setId(id);
            activity.setName(name);
            activity.setSummary(summary);
            activity.setThemeKey(themeKey);
            activity.setStartTime(startTime);
            activity.setEndTime(endTime);
            activity.setDailyDrawLimit(dailyDrawLimit);
            activity.setPointCost(pointCost);
            activity.setNoWinWeight(noWinWeight);
            return activity;
        }
    }

    public record RuleInput(String title, String content) {
        ActivityRule toEntity() {
            ActivityRule rule = new ActivityRule();
            rule.setTitle(title);
            rule.setContent(content);
            return rule;
        }
    }

    public record ActivityPrizeInput(
            String prizeId,
            Integer weight,
            Integer allocatedInventory,
            Integer status
    ) {
        ActivityPrize toEntity(Long activityId, Long activityPrizeId) {
            ActivityPrize activityPrize = new ActivityPrize();
            activityPrize.setId(activityPrizeId);
            activityPrize.setActivityId(activityId);
            activityPrize.setPrizeId(parseId(prizeId, "prizeId"));
            activityPrize.setWeight(weight);
            activityPrize.setAllocatedInventory(allocatedInventory);
            // 运营配置阶段尚未发生正式消耗，调整总分配库存时剩余库存保持相同起点。
            activityPrize.setRemainingInventory(allocatedInventory);
            activityPrize.setStatus(status);
            return activityPrize;
        }
    }

    public record ActivityView(
            String id,
            String name,
            String summary,
            String themeKey,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer dailyDrawLimit,
            Integer pointCost,
            Integer noWinWeight,
            Integer status,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
        static ActivityView from(Activity activity) {
            return new ActivityView(
                    Long.toString(activity.getId()),
                    activity.getName(),
                    activity.getSummary(),
                    activity.getThemeKey(),
                    activity.getStartTime(),
                    activity.getEndTime(),
                    activity.getDailyDrawLimit(),
                    activity.getPointCost(),
                    activity.getNoWinWeight(),
                    activity.getStatus(),
                    activity.getCreateTime(),
                    activity.getUpdateTime()
            );
        }
    }

    public record RuleView(String id, String title, String content, Integer sortOrder) {
        static RuleView from(ActivityRule rule) {
            return new RuleView(
                    Long.toString(rule.getId()),
                    rule.getTitle(),
                    rule.getContent(),
                    rule.getSortOrder()
            );
        }
    }

    public record ActivityPrizeView(
            String id,
            String activityId,
            String prizeId,
            Integer weight,
            Integer allocatedInventory,
            Integer remainingInventory,
            Integer status,
            Integer prizeTypeSnapshot
    ) {
        static ActivityPrizeView from(ActivityPrize prize) {
            if (prize == null) {
                throw new BusinessException(ApiErrorCodes.RESOURCE_NOT_FOUND, "活动奖品不存在");
            }
            return new ActivityPrizeView(
                    Long.toString(prize.getId()),
                    Long.toString(prize.getActivityId()),
                    Long.toString(prize.getPrizeId()),
                    prize.getWeight(),
                    prize.getAllocatedInventory(),
                    prize.getRemainingInventory(),
                    prize.getStatus(),
                    prize.getPrizeTypeSnapshot()
            );
        }
    }

    public record ActivityDetail(
            ActivityView activity,
            List<RuleView> rules,
            List<ActivityPrizeView> prizes
    ) {
    }
}
