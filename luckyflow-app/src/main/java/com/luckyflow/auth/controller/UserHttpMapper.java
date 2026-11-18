package com.luckyflow.auth.controller;

import com.luckyflow.auth.dto.AuthDtos;
import com.luckyflow.auth.dto.MeDtos;
import com.luckyflow.auth.service.AuthApplicationService;
import com.luckyflow.user.constant.UserRoleConstants;
import com.luckyflow.user.entity.User;

import java.util.List;

/** 将内部账号实体映射为不含密码哈希和完整联系方式的 HTTP DTO。 */
final class UserHttpMapper {

    static AuthDtos.RegisteredUserResponse registered(User user) {
        return new AuthDtos.RegisteredUserResponse(
                Long.toString(user.getId()),
                user.getAccount(),
                user.getNickname(),
                user.getAvatarKey()
        );
    }

    static AuthDtos.CurrentUserResponse current(User user) {
        return new AuthDtos.CurrentUserResponse(
                Long.toString(user.getId()),
                user.getAccount(),
                user.getNickname(),
                user.getAvatarKey(),
                maskEmail(user.getEmail()),
                maskPhone(user.getPhone()),
                roles(user)
        );
    }

    static MeDtos.ProfileResponse profile(User user) {
        return new MeDtos.ProfileResponse(
                Long.toString(user.getId()),
                user.getAccount(),
                user.getNickname(),
                user.getBirthDate(),
                user.getAvatarKey(),
                maskEmail(user.getEmail()),
                maskPhone(user.getPhone()),
                // 只公开是否已设密码，绝不返回哈希或任何可用于验证的材料。
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
                roles(user)
        );
    }

    static AuthDtos.TokenResponse token(AuthApplicationService.AuthenticatedSession session) {
        return new AuthDtos.TokenResponse(
                session.token().value(),
                "Bearer",
                session.token().expiresInSeconds(),
                current(session.user())
        );
    }

    private static List<String> roles(User user) {
        return List.of(user.getRole() == null || user.getRole().isBlank()
                ? UserRoleConstants.USER
                : user.getRole());
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (phone.length() <= 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private UserHttpMapper() {
    }
}
