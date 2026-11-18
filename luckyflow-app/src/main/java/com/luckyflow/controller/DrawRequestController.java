package com.luckyflow.controller;

import com.luckyflow.publicapi.DrawRequestQueryService;
import com.luckyflow.publicapi.HttpIdParser;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.security.CurrentUserAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 本人原抽奖请求和换设备未决请求查询；任何 GET 都不会重新执行抽奖。 */
@RestController
@RequestMapping("/api/draw-requests")
public class DrawRequestController {

    private final DrawRequestQueryService drawRequestQueryService;
    private final CurrentUserAccessor currentUserAccessor;

    public DrawRequestController(
            DrawRequestQueryService drawRequestQueryService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.drawRequestQueryService = drawRequestQueryService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping("/pending")
    public ApiResponse<DrawRequestQueryService.DrawRequestResponse> findLatestPending(
            @RequestParam String activityId
    ) {
        return ApiResponse.success(drawRequestQueryService.findLatestPending(
                currentUserAccessor.requireUserId(),
                HttpIdParser.requirePositiveLong(activityId, "activityId")
        ));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<DrawRequestQueryService.DrawRequestResponse> findRequest(
            @PathVariable String requestId,
            @RequestParam String activityId
    ) {
        return ApiResponse.success(drawRequestQueryService.findOwnedRequest(
                requestId,
                currentUserAccessor.requireUserId(),
                HttpIdParser.requirePositiveLong(activityId, "activityId")
        ));
    }
}
