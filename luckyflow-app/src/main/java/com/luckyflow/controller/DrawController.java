package com.luckyflow.controller;

import com.luckyflow.lottery.service.Interface.DrawService;
import com.luckyflow.publicapi.DrawRequestQueryService.DrawResultResponse;
import com.luckyflow.publicapi.HttpIdParser;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.security.CurrentUserAccessor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对外暴露要求调用方提供稳定幂等键的抽奖入口。 */
@RestController
@RequestMapping("/api/draws")
public class DrawController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final DrawService drawService;
    private final CurrentUserAccessor currentUserAccessor;

    public DrawController(
            DrawService drawService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.drawService = drawService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @PostMapping
    public ApiResponse<DrawResultResponse> draw(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String requestId,
            @RequestBody DrawHttpRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Draw request body must not be null");
        }
        return ApiResponse.success(DrawResultResponse.from(drawService.draw(
                requestId,
                currentUserAccessor.requireUserId(),
                HttpIdParser.requirePositiveLong(request.activityId(), "activityId")
        )));
    }

    public record DrawHttpRequest(String activityId) {
    }
}
