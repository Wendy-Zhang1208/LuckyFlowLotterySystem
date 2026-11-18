package com.luckyflow.controller;

import com.luckyflow.publicapi.DrawEligibilityService;
import com.luckyflow.publicapi.HttpIdParser;
import com.luckyflow.publicapi.PublicActivityQueryService;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import com.luckyflow.security.CurrentUserAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 公开活动广场、详情及登录用户参与资格接口。 */
@RestController
@RequestMapping("/api/activities")
public class PublicActivityController {

    private final PublicActivityQueryService publicActivityQueryService;
    private final DrawEligibilityService eligibilityService;
    private final CurrentUserAccessor currentUserAccessor;

    public PublicActivityController(
            PublicActivityQueryService publicActivityQueryService,
            DrawEligibilityService eligibilityService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.publicActivityQueryService = publicActivityQueryService;
        this.eligibilityService = eligibilityService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public ApiResponse<PageResponse<PublicActivityQueryService.ActivitySummary>> findActivities(
            @RequestParam(defaultValue = "ONGOING") String status,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "9") int size
    ) {
        return ApiResponse.success(
                publicActivityQueryService.findActivities(status, keyword, page, size)
        );
    }

    @GetMapping("/{activityId}")
    public ApiResponse<PublicActivityQueryService.ActivityDetail> findDetail(
            @PathVariable String activityId
    ) {
        return ApiResponse.success(publicActivityQueryService.findDetail(
                HttpIdParser.requirePositiveLong(activityId, "activityId")
        ));
    }

    @GetMapping("/{activityId}/eligibility")
    public ApiResponse<DrawEligibilityService.Eligibility> findEligibility(
            @PathVariable String activityId
    ) {
        long trustedUserId = currentUserAccessor.requireUserId();
        return ApiResponse.success(eligibilityService.evaluate(
                trustedUserId,
                HttpIdParser.requirePositiveLong(activityId, "activityId")
        ));
    }
}
