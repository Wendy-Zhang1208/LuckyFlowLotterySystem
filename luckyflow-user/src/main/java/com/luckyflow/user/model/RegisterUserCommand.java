package com.luckyflow.user.model;

import java.time.LocalDate;

/** 已完成验证码校验后交给用户域的注册命令。 */
public record RegisterUserCommand(
        String email,
        String phone,
        String passwordHash,
        String nickname,
        LocalDate birthDate,
        String avatarKey
) {
}
