package com.luckyflow.auth.controller;

import com.luckyflow.auth.dto.AuthDtos;
import com.luckyflow.auth.model.VerificationPurpose;
import com.luckyflow.auth.service.AuthApplicationService;
import com.luckyflow.auth.service.VerificationCodeService;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.security.CurrentUserAccessor;
import com.luckyflow.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 注册、登录、验证码与当前认证身份接口。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final VerificationCodeService verificationCodeService;
    private final AuthApplicationService authService;
    private final CurrentUserAccessor currentUserAccessor;

    public AuthController(
            VerificationCodeService verificationCodeService,
            AuthApplicationService authService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.verificationCodeService = verificationCodeService;
        this.authService = authService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @PostMapping("/verification-codes")
    public ApiResponse<Void> issueVerificationCode(
            @Valid @RequestBody AuthDtos.VerificationCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        if (request.purpose() == VerificationPurpose.BIND) {
            currentUserAccessor.requireUserId();
        }
        // 只使用容器确认的对端地址；是否信任代理转发头应由部署层显式配置。
        verificationCodeService.issue(
                request.channel(),
                request.purpose(),
                request.target(),
                servletRequest.getRemoteAddr()
        );
        return ApiResponse.success(null);
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.RegisteredUserResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request
    ) {
        User user = authService.register(
                request.channel(),
                request.target(),
                request.code(),
                request.password(),
                request.nickname(),
                request.birthDate(),
                request.avatarKey()
        );
        return ApiResponse.success(UserHttpMapper.registered(user));
    }

    @PostMapping("/login/password")
    public ApiResponse<AuthDtos.TokenResponse> loginWithPassword(
            @Valid @RequestBody AuthDtos.PasswordLoginRequest request
    ) {
        return ApiResponse.success(UserHttpMapper.token(
                authService.loginWithPassword(request.username(), request.password())
        ));
    }

    @PostMapping("/login/code")
    public ApiResponse<AuthDtos.TokenResponse> loginWithCode(
            @Valid @RequestBody AuthDtos.CodeLoginRequest request
    ) {
        return ApiResponse.success(UserHttpMapper.token(
                authService.loginWithCode(request.channel(), request.target(), request.code())
        ));
    }

    @GetMapping("/me")
    public ApiResponse<AuthDtos.CurrentUserResponse> me() {
        return ApiResponse.success(UserHttpMapper.current(
                authService.currentUser(currentUserAccessor.requireUserId())
        ));
    }
}
