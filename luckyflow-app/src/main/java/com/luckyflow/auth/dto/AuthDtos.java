package com.luckyflow.auth.dto;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 认证 HTTP 契约；所有 Long 标识在 DTO 边界转成十进制字符串。 */
public final class AuthDtos {

    public record VerificationCodeRequest(
            @NotNull VerificationChannel channel,
            @NotNull VerificationPurpose purpose,
            @NotBlank @Size(max = 320) String target
    ) {
    }

    public record RegisterRequest(
            @NotNull VerificationChannel channel,
            @NotBlank @Size(max = 320) String target,
            @NotBlank @Size(min = 6, max = 6) String code,
            @Size(min = 8, max = 72) String password,
            @Size(max = 64) String nickname,
            LocalDate birthDate,
            String avatarKey
    ) {
    }

    public record PasswordLoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 256) String password
    ) {
    }

    public record CodeLoginRequest(
            @NotNull VerificationChannel channel,
            @NotBlank @Size(max = 320) String target,
            @NotBlank @Size(min = 6, max = 6) String code
    ) {
    }

    public record RegisteredUserResponse(
            String userId,
            String username,
            String nickname,
            String avatarKey
    ) {
    }

    public record CurrentUserResponse(
            String userId,
            String username,
            String nickname,
            String avatarKey,
            String emailMasked,
            String phoneMasked,
            List<String> roles
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            CurrentUserResponse user
    ) {
    }

    private AuthDtos() {
    }
}
