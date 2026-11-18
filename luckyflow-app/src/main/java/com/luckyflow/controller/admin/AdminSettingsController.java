package com.luckyflow.controller.admin;

import com.luckyflow.admin.settings.AdminSettingsService;
import com.luckyflow.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端安全配置状态只读接口，不提供在线修改密钥或基础设施连接信息的能力。 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    private final AdminSettingsService settingsService;

    public AdminSettingsController(AdminSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<AdminSettingsService.SafeSettingsView> getSettings() {
        return ApiResponse.success(settingsService.getSafeSettings());
    }
}
