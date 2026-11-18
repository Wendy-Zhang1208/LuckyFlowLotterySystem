package com.luckyflow.controller.admin;

import com.luckyflow.admin.notification.AdminNotificationQueryService;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端仅可查看脱敏通知状态；这里刻意不提供人工重发入口。 */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final AdminNotificationQueryService queryService;

    public AdminNotificationController(AdminNotificationQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationQueryService.NotificationSummary>> findPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel
    ) {
        return ApiResponse.success(queryService.findPage(page, size, status, channel));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminNotificationQueryService.NotificationDetail> findDetail(
            @PathVariable long id
    ) {
        return ApiResponse.success(queryService.findDetail(id));
    }
}
