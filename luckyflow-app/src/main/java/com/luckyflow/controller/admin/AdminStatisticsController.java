package com.luckyflow.controller.admin;

import com.luckyflow.admin.statistics.AdminStatisticsService;
import com.luckyflow.response.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 管理端基础统计接口，时间按 Asia/Shanghai 的无偏移 LocalDateTime 解释。 */
@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {

    private final AdminStatisticsService statisticsService;

    public AdminStatisticsController(AdminStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminStatisticsService.StatisticsOverview> overview(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,
            @RequestParam(required = false) Long activityId
    ) {
        return ApiResponse.success(statisticsService.overview(startTime, endTime, activityId));
    }
}
