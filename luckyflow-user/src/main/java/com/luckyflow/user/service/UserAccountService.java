package com.luckyflow.user.service;

import com.luckyflow.user.entity.User;
import com.luckyflow.user.model.RegisterUserCommand;

import java.time.LocalDate;
import java.util.List;

/** 认证与本人资料场景使用的账号领域服务。 */
public interface UserAccountService {

    record UserPage(List<User> records, long total) {
        public UserPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    User registerVerifiedUser(RegisterUserCommand command);

    User findByUsername(String username);

    User findByEmail(String email);

    User findByPhone(String phone);

    User getRequired(Long userId);

    User updateProfile(Long userId, String nickname, LocalDate birthDate, String avatarKey);

    void replacePassword(Long userId, String expectedHash, String newHash);

    User bindVerifiedEmail(Long userId, String email);

    User bindVerifiedPhone(Long userId, String phone);

    User removeEmail(Long userId);

    User removePhone(Long userId);

    /** 管理端只读、脱敏展示的账号分页；密码哈希不会离开 User 模块。 */
    UserPage findUsers(String keyword, Integer status, int page, int size);
}
