package com.luckyflow.auth.controller;

import com.luckyflow.auth.dto.MeDtos;
import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.service.AuthApplicationService;
import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.security.CurrentUserAccessor;
import com.luckyflow.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** 当前用户资料、密码和已验证联系方式接口。 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthApplicationService authService;
    private final CurrentUserAccessor currentUserAccessor;

    public MeController(
            AuthApplicationService authService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.authService = authService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public ApiResponse<MeDtos.ProfileResponse> profile() {
        return ApiResponse.success(UserHttpMapper.profile(
                authService.currentUser(currentUserAccessor.requireUserId())
        ));
    }

    @PatchMapping
    public ApiResponse<MeDtos.ProfileResponse> updateProfile(
            @Valid @RequestBody MeDtos.ProfilePatchRequest request
    ) {
        User user = authService.updateProfile(
                currentUserAccessor.requireUserId(),
                request.nickname(),
                request.birthDate(),
                request.avatarKey()
        );
        return ApiResponse.success(UserHttpMapper.profile(user));
    }

    @PostMapping("/password")
    public ApiResponse<Void> setOrChangePassword(
            @Valid @RequestBody MeDtos.PasswordRequest request
    ) {
        authService.setOrChangePassword(
                currentUserAccessor.requireUserId(),
                request.currentPassword(),
                request.newPassword(),
                request.resetChannel(),
                request.resetTarget(),
                request.resetCode()
        );
        return ApiResponse.success(null);
    }

    @PutMapping("/contacts/{channel}")
    public ApiResponse<MeDtos.ProfileResponse> bindContact(
            @PathVariable String channel,
            @Valid @RequestBody MeDtos.BindContactRequest request
    ) {
        User user = authService.bindContact(
                currentUserAccessor.requireUserId(),
                parseChannel(channel),
                request.target(),
                request.code()
        );
        return ApiResponse.success(UserHttpMapper.profile(user));
    }

    @DeleteMapping("/contacts/{channel}")
    public ApiResponse<MeDtos.ProfileResponse> removeContact(@PathVariable String channel) {
        User user = authService.removeContact(
                currentUserAccessor.requireUserId(),
                parseChannel(channel)
        );
        return ApiResponse.success(UserHttpMapper.profile(user));
    }

    private VerificationChannel parseChannel(String value) {
        try {
            return VerificationChannel.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalidChannel) {
            throw new BusinessException(
                    ApiErrorCodes.VALIDATION_FAILED,
                    "联系方式渠道只能是 EMAIL 或 SMS"
            );
        }
    }
}
