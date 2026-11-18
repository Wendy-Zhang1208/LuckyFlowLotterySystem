package com.luckyflow.auth.dto;

import com.luckyflow.auth.model.VerificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 本人资料、密码和联系方式 HTTP 契约。 */
public final class MeDtos {

    public record ProfileResponse(
            String userId,
            String username,
            String nickname,
            LocalDate birthDate,
            String avatarKey,
            String emailMasked,
            String phoneMasked,
            boolean hasPassword,
            List<String> roles
    ) {
    }

    public record ProfilePatchRequest(
            @Size(max = 64) String nickname,
            LocalDate birthDate,
            String avatarKey
    ) {
    }

    public record PasswordRequest(
            String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword,
            VerificationChannel resetChannel,
            @Size(max = 320) String resetTarget,
            @Size(min = 6, max = 6) String resetCode
    ) {
    }

    public record BindContactRequest(
            @NotBlank @Size(max = 320) String target,
            @NotBlank @Size(min = 6, max = 6) String code
    ) {
    }

    private MeDtos() {
    }
}
