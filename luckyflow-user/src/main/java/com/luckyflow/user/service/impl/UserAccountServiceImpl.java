package com.luckyflow.user.service.impl;

import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.user.constant.AvatarKeyConstants;
import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.entity.User;
import com.luckyflow.user.mapper.PointAccountMapper;
import com.luckyflow.user.mapper.UserMapper;
import com.luckyflow.user.model.RegisterUserCommand;
import com.luckyflow.user.service.UserAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import static com.luckyflow.user.constant.UserRoleConstants.USER;
import static com.luckyflow.user.constant.UserStatusConstants.USER_STATUS_ENABLED;

/** 用户、已验证联系方式和积分账户的一致性边界。 */
@Service
public class UserAccountServiceImpl implements UserAccountService {

    private static final int USERNAME_INSERT_ATTEMPTS = 8;
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long USERNAME_SUFFIX_SPACE = 78_364_164_096L; // 36^7；加 lf_ 后恰好适配旧库 varchar(10)。

    private final UserMapper userMapper;
    private final PointAccountMapper pointAccountMapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public UserAccountServiceImpl(
            UserMapper userMapper,
            PointAccountMapper pointAccountMapper,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.pointAccountMapper = pointAccountMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public User registerVerifiedUser(RegisterUserCommand command) {
        Objects.requireNonNull(command, "Register command must not be null");
        boolean hasEmail = hasText(command.email());
        boolean hasPhone = hasText(command.phone());
        if (hasEmail == hasPhone) {
            throw invalid("注册时必须且只能提供一种已验证联系方式");
        }
        validateBirthDate(command.birthDate());
        String nickname = normalizeNickname(command.nickname());
        String avatarKey = hasText(command.avatarKey())
                ? command.avatarKey()
                : AvatarKeyConstants.random(random);
        validateAvatar(avatarKey);
        rejectExistingContact(command.email(), command.phone(), false);

        LocalDateTime now = LocalDateTime.now(clock);
        for (int attempt = 0; attempt < USERNAME_INSERT_ATTEMPTS; attempt++) {
            User user = new User();
            user.setAccount(generateUsername());
            user.setNickname(nickname);
            user.setPasswordHash(command.passwordHash());
            user.setBirthDate(command.birthDate());
            user.setAvatarKey(avatarKey);
            user.setEmail(hasEmail ? command.email() : null);
            user.setEmailVerifiedAt(hasEmail ? now : null);
            user.setPhone(hasPhone ? command.phone() : null);
            user.setPhoneVerifiedAt(hasPhone ? now : null);
            user.setRole(USER);
            user.setStatus(USER_STATUS_ENABLED);
            user.setCreateTime(now);
            user.setUpdateTime(now);

            try {
                if (userMapper.insert(user) != 1 || user.getId() == null) {
                    throw new IllegalStateException("Failed to create user account");
                }
            } catch (DuplicateKeyException duplicate) {
                // 唯一用户名冲突可安全重试；联系方式竞争必须稳定返回“已注册”。
                rejectExistingContact(command.email(), command.phone(), true);
                continue;
            }

            PointAccount pointAccount = new PointAccount();
            pointAccount.setUserId(user.getId());
            pointAccount.setBalance(0L);
            pointAccount.setCreateTime(now);
            pointAccount.setUpdateTime(now);
            if (pointAccountMapper.insert(pointAccount) != 1) {
                throw new IllegalStateException("Failed to initialize point account");
            }
            return user;
        }
        throw new BusinessException(ApiErrorCodes.CONFLICT, "暂时无法生成账号，请您稍后重试");
    }

    @Override
    public User findByUsername(String username) {
        return hasText(username) ? userMapper.selectByAccount(username.trim()) : null;
    }

    @Override
    public User findByEmail(String email) {
        return hasText(email) ? userMapper.selectByEmail(email) : null;
    }

    @Override
    public User findByPhone(String phone) {
        return hasText(phone) ? userMapper.selectByPhone(phone) : null;
    }

    @Override
    public User getRequired(Long userId) {
        requirePositiveUserId(userId);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ApiErrorCodes.RESOURCE_NOT_FOUND, "您的账号不存在");
        }
        return user;
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, String nickname, LocalDate birthDate, String avatarKey) {
        User current = lockRequired(userId);
        String normalizedNickname = normalizeNickname(nickname);
        validateBirthDate(birthDate);
        validateAvatar(avatarKey);
        LocalDateTime now = LocalDateTime.now(clock);
        if (userMapper.updateProfile(userId, normalizedNickname, birthDate, avatarKey, now) != 1) {
            throw new BusinessException(ApiErrorCodes.CONCURRENT_ACCOUNT_UPDATE, "资料已发生变化，请您刷新后重试");
        }
        current.setNickname(normalizedNickname);
        current.setBirthDate(birthDate);
        current.setAvatarKey(avatarKey);
        current.setUpdateTime(now);
        return current;
    }

    @Override
    @Transactional
    public void replacePassword(Long userId, String expectedHash, String newHash) {
        if (!hasText(newHash)) {
            throw invalid("新密码不能为空");
        }
        User current = lockRequired(userId);
        if (!Objects.equals(current.getPasswordHash(), expectedHash)) {
            throw new BusinessException(ApiErrorCodes.CONCURRENT_ACCOUNT_UPDATE, "密码已发生变化，请您重新操作");
        }
        if (userMapper.replacePassword(userId, expectedHash, newHash, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ApiErrorCodes.CONCURRENT_ACCOUNT_UPDATE, "密码已发生变化，请您重新操作");
        }
    }

    @Override
    @Transactional
    public User bindVerifiedEmail(Long userId, String email) {
        requireText(email, "邮箱不能为空");
        User user = lockRequired(userId);
        if (Objects.equals(user.getEmail(), email) && user.getEmailVerifiedAt() != null) {
            return user;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (userMapper.updateEmail(userId, email, now, now) != 1) {
                throw concurrentContactUpdate();
            }
        } catch (DuplicateKeyException duplicate) {
            throw contactAlreadyRegistered();
        }
        user.setEmail(email);
        user.setEmailVerifiedAt(now);
        return user;
    }

    @Override
    @Transactional
    public User bindVerifiedPhone(Long userId, String phone) {
        requireText(phone, "手机号不能为空");
        User user = lockRequired(userId);
        if (Objects.equals(user.getPhone(), phone) && user.getPhoneVerifiedAt() != null) {
            return user;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (userMapper.updatePhone(userId, phone, now, now) != 1) {
                throw concurrentContactUpdate();
            }
        } catch (DuplicateKeyException duplicate) {
            throw contactAlreadyRegistered();
        }
        user.setPhone(phone);
        user.setPhoneVerifiedAt(now);
        return user;
    }

    @Override
    @Transactional
    public User removeEmail(Long userId) {
        User user = lockRequired(userId);
        if (!hasText(user.getEmail()) || user.getEmailVerifiedAt() == null) {
            throw new BusinessException(ApiErrorCodes.CONTACT_NOT_OWNED, "您的账号未绑定已验证邮箱");
        }
        requireOtherVerifiedContact(user.getPhone(), user.getPhoneVerifiedAt());
        LocalDateTime now = LocalDateTime.now(clock);
        if (userMapper.updateEmail(userId, null, null, now) != 1) {
            throw concurrentContactUpdate();
        }
        user.setEmail(null);
        user.setEmailVerifiedAt(null);
        return user;
    }

    @Override
    @Transactional
    public User removePhone(Long userId) {
        User user = lockRequired(userId);
        if (!hasText(user.getPhone()) || user.getPhoneVerifiedAt() == null) {
            throw new BusinessException(ApiErrorCodes.CONTACT_NOT_OWNED, "您的账号未绑定已验证手机号");
        }
        requireOtherVerifiedContact(user.getEmail(), user.getEmailVerifiedAt());
        LocalDateTime now = LocalDateTime.now(clock);
        if (userMapper.updatePhone(userId, null, null, now) != 1) {
            throw concurrentContactUpdate();
        }
        user.setPhone(null);
        user.setPhoneVerifiedAt(null);
        return user;
    }

    @Override
    public UserPage findUsers(String keyword, Integer status, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("分页参数不合法");
        }
        if (status != null && status != 0 && status != 1) {
            throw invalid("用户状态不合法");
        }
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        int offset = Math.multiplyExact(page - 1, size);
        return new UserPage(
                userMapper.selectAdminPage(normalizedKeyword, status, offset, size),
                userMapper.countAdminPage(normalizedKeyword, status)
        );
    }

    private User lockRequired(Long userId) {
        requirePositiveUserId(userId);
        User user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ApiErrorCodes.RESOURCE_NOT_FOUND, "您的账号不存在");
        }
        return user;
    }

    private void rejectExistingContact(String email, String phone, boolean currentRead) {
        User existing = null;
        if (hasText(email)) {
            existing = currentRead ? userMapper.selectByEmailForShare(email) : userMapper.selectByEmail(email);
        } else if (hasText(phone)) {
            existing = currentRead ? userMapper.selectByPhoneForShare(phone) : userMapper.selectByPhone(phone);
        }
        if (existing != null) {
            throw contactAlreadyRegistered();
        }
    }

    private void requireOtherVerifiedContact(String contact, LocalDateTime verifiedAt) {
        if (!hasText(contact) || verifiedAt == null) {
            throw new BusinessException(
                    ApiErrorCodes.LAST_VERIFIED_CONTACT,
                    "请您先绑定另一种已验证联系方式"
            );
        }
    }

    private String generateUsername() {
        long value = Long.remainderUnsigned(random.nextLong(), USERNAME_SUFFIX_SPACE);
        String suffix = Long.toString(value, 36);
        return "lf_" + "0".repeat(7 - suffix.length()) + suffix;
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        String normalized = nickname.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_NICKNAME_LENGTH) {
            throw invalid("昵称不能超过 64 个字符");
        }
        return normalized;
    }

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw invalid("搜索关键词过长");
        }
        return normalized;
    }

    private void validateBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.isAfter(LocalDate.now(clock))) {
            throw invalid("出生日期不能晚于今天");
        }
    }

    private void validateAvatar(String avatarKey) {
        if (!AvatarKeyConstants.isSupported(avatarKey)) {
            throw invalid("请选择有效的云朵头像");
        }
    }

    private void requirePositiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw invalid("用户标识不合法");
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw invalid(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ApiErrorCodes.VALIDATION_FAILED, message);
    }

    private BusinessException contactAlreadyRegistered() {
        return new BusinessException(
                ApiErrorCodes.CONTACT_ALREADY_REGISTERED,
                "该联系方式已注册，请您直接登录"
        );
    }

    private BusinessException concurrentContactUpdate() {
        return new BusinessException(
                ApiErrorCodes.CONCURRENT_ACCOUNT_UPDATE,
                "账号已发生变化，请您刷新后重试"
        );
    }
}
