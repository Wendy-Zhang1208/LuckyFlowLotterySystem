package com.luckyflow.auth.service;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;
import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.user.constant.UserRoleConstants;
import com.luckyflow.user.constant.UserStatusConstants;
import com.luckyflow.user.entity.User;
import com.luckyflow.user.model.RegisterUserCommand;
import com.luckyflow.user.service.UserAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** HTTP 认证用例编排；验证码消费、密码哈希和用户事务各自保持清晰边界。 */
@Service
public class AuthApplicationService {

    private final UserAccountService userAccountService;
    private final VerificationCodeService verificationCodeService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthApplicationService(
            UserAccountService userAccountService,
            VerificationCodeService verificationCodeService,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountService = userAccountService;
        this.verificationCodeService = verificationCodeService;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        // 账号不存在时仍执行一次 BCrypt，降低密码登录的用户枚举时序差异。
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public User register(
            VerificationChannel channel,
            String target,
            String code,
            String password,
            String nickname,
            LocalDate birthDate,
            String avatarKey
    ) {
        String normalizedTarget = verificationCodeService.consume(
                channel,
                VerificationPurpose.REGISTER,
                target,
                code
        );
        String passwordHash = password == null ? null : passwordEncoder.encode(validatePassword(password));
        return userAccountService.registerVerifiedUser(new RegisterUserCommand(
                channel == VerificationChannel.EMAIL ? normalizedTarget : null,
                channel == VerificationChannel.SMS ? normalizedTarget : null,
                passwordHash,
                nickname,
                birthDate,
                avatarKey
        ));
    }

    public AuthenticatedSession loginWithPassword(String username, String password) {
        User user = userAccountService.findByUsername(username);
        String storedHash = user == null || user.getPasswordHash() == null
                ? dummyPasswordHash
                : user.getPasswordHash();
        boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(password, storedHash);
        } catch (RuntimeException malformedLegacyHash) {
            passwordMatches = false;
        }
        if (user == null || user.getPasswordHash() == null || !passwordMatches) {
            throw invalidCredentials();
        }
        requireEnabled(user);
        return new AuthenticatedSession(user, jwtTokenService.issue(user));
    }

    public AuthenticatedSession loginWithCode(
            VerificationChannel channel,
            String target,
            String code
    ) {
        String normalizedTarget = verificationCodeService.consume(
                channel,
                VerificationPurpose.LOGIN,
                target,
                code
        );
        User user = channel == VerificationChannel.EMAIL
                ? userAccountService.findByEmail(normalizedTarget)
                : userAccountService.findByPhone(normalizedTarget);
        if (user == null || !isVerifiedContact(user, channel, normalizedTarget)) {
            throw invalidCredentials();
        }
        requireEnabled(user);
        return new AuthenticatedSession(user, jwtTokenService.issue(user));
    }

    public User currentUser(long userId) {
        User user = userAccountService.getRequired(userId);
        requireEnabled(user);
        return user;
    }

    public User updateProfile(
            long userId,
            String nickname,
            LocalDate birthDate,
            String avatarKey
    ) {
        User current = currentUser(userId);
        return userAccountService.updateProfile(
                userId,
                nickname == null ? current.getNickname() : nickname,
                birthDate == null ? current.getBirthDate() : birthDate,
                avatarKey == null ? current.getAvatarKey() : avatarKey
        );
    }

    public void setOrChangePassword(
            long userId,
            String currentPassword,
            String newPassword,
            VerificationChannel resetChannel,
            String resetTarget,
            String resetCode
    ) {
        User user = currentUser(userId);
        String existingHash = user.getPasswordHash();
        if (existingHash != null) {
            boolean currentPasswordAccepted = hasText(currentPassword)
                    && safePasswordMatches(currentPassword, existingHash);
            if (!currentPasswordAccepted) {
                verifyPasswordReset(user, resetChannel, resetTarget, resetCode);
            }
        }
        userAccountService.replacePassword(
                userId,
                existingHash,
                passwordEncoder.encode(validatePassword(newPassword))
        );
    }

    public User bindContact(
            long userId,
            VerificationChannel channel,
            String target,
            String code
    ) {
        currentUser(userId);
        String normalizedTarget = verificationCodeService.consume(
                channel,
                VerificationPurpose.BIND,
                target,
                code
        );
        return channel == VerificationChannel.EMAIL
                ? userAccountService.bindVerifiedEmail(userId, normalizedTarget)
                : userAccountService.bindVerifiedPhone(userId, normalizedTarget);
    }

    public User removeContact(long userId, VerificationChannel channel) {
        currentUser(userId);
        return channel == VerificationChannel.EMAIL
                ? userAccountService.removeEmail(userId)
                : userAccountService.removePhone(userId);
    }

    public String effectiveRole(User user) {
        return hasText(user.getRole()) ? user.getRole() : UserRoleConstants.USER;
    }

    private void verifyPasswordReset(
            User user,
            VerificationChannel channel,
            String target,
            String code
    ) {
        if (channel == null || !hasText(target) || !hasText(code)) {
            throw new BusinessException(
                    ApiErrorCodes.CURRENT_PASSWORD_INVALID,
                    "当前密码不正确，请您重新输入或使用验证码重置"
            );
        }
        String normalizedTarget = ContactNormalizer.normalize(channel, target);
        if (!isVerifiedContact(user, channel, normalizedTarget)) {
            throw new BusinessException(
                    ApiErrorCodes.CONTACT_NOT_OWNED,
                    "请您使用本账号已验证的联系方式"
            );
        }
        verificationCodeService.consume(
                channel,
                VerificationPurpose.PASSWORD_RESET,
                normalizedTarget,
                code
        );
    }

    private boolean isVerifiedContact(User user, VerificationChannel channel, String normalizedTarget) {
        return channel == VerificationChannel.EMAIL
                ? Objects.equals(user.getEmail(), normalizedTarget) && user.getEmailVerifiedAt() != null
                : Objects.equals(user.getPhone(), normalizedTarget) && user.getPhoneVerifiedAt() != null;
    }

    private boolean safePasswordMatches(String raw, String encoded) {
        try {
            return passwordEncoder.matches(raw, encoded);
        } catch (RuntimeException malformedLegacyHash) {
            return false;
        }
    }

    private String validatePassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    ApiErrorCodes.VALIDATION_FAILED,
                    "密码需为 8 至 72 个字节"
            );
        }
        return password;
    }

    private void requireEnabled(User user) {
        if (!Integer.valueOf(UserStatusConstants.USER_STATUS_ENABLED).equals(user.getStatus())) {
            throw new BusinessException(ApiErrorCodes.USER_DISABLED, "您的账号当前不可用");
        }
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                ApiErrorCodes.INVALID_CREDENTIALS,
                "账号或登录凭据不正确"
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AuthenticatedSession(User user, JwtTokenService.IssuedToken token) {
    }
}
