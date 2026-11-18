package com.luckyflow.auth.service;

import com.luckyflow.auth.config.AuthProperties;
import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;
import com.luckyflow.auth.verification.VerificationCodeStore;
import com.luckyflow.auth.verification.VerificationMessageSender;
import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/** 验证码签发和一次性消费应用服务。 */
@Service
public class VerificationCodeService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Logger LOGGER = LoggerFactory.getLogger(VerificationCodeService.class);

    private final VerificationCodeStore store;
    private final VerificationMessageSender sender;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public VerificationCodeService(
            VerificationCodeStore store,
            VerificationMessageSender sender,
            AuthProperties properties
    ) {
        this.store = store;
        this.sender = sender;
        this.properties = properties;
        validateConfiguration();
    }

    /**
     * 签发成功仅表示真实 Sender 已正常返回；响应和日志均不会得到验证码。
     */
    public void issue(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String target,
            String clientAddress
    ) {
        if (purpose == null) {
            throw invalid("请您选择验证码用途");
        }
        String normalizedTarget = ContactNormalizer.normalize(channel, target);
        String scopeHash = scopeHash(channel, purpose, normalizedTarget);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String codeHash = hmac(scopeHash + "|" + code);
        String clientHash = hmac("client|" + safeClientAddress(clientAddress));

        VerificationCodeStore.IssueResult result = store.issue(scopeHash, codeHash, clientHash);
        switch (result) {
            case ISSUED -> {
                try {
                    sender.send(
                            channel,
                            purpose,
                            normalizedTarget,
                            code,
                            properties.getVerification().getCodeTtl()
                    );
                } catch (RuntimeException providerFailure) {
                    store.revoke(scopeHash, codeHash);
                    // 只记录稳定异常类型，禁止把验证码或完整联系方式写进日志。
                    LOGGER.warn(
                            "Verification provider failed, channel={}, purpose={}, type={}",
                            channel,
                            purpose,
                            providerFailure.getClass().getSimpleName()
                    );
                    throw new BusinessException(
                            ApiErrorCodes.VERIFICATION_PROVIDER_UNAVAILABLE,
                            "验证码暂时无法发送，请您稍后重试"
                    );
                }
            }
            case SEND_INTERVAL -> throw new BusinessException(
                    ApiErrorCodes.RATE_LIMITED,
                    "验证码发送过于频繁，请您稍后再试"
            );
            case TARGET_HOURLY_LIMIT, TARGET_DAILY_LIMIT, IP_HOURLY_LIMIT -> throw new BusinessException(
                    ApiErrorCodes.RATE_LIMITED,
                    "验证码发送次数已达上限，请您稍后再试"
            );
        }
    }

    /** 消费成功后同一验证码立即失效。返回规范化后的联系方式供后续可信写入。 */
    public String consume(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String target,
            String code
    ) {
        if (purpose == null || code == null || !code.matches("\\d{6}")) {
            throw invalidCode();
        }
        String normalizedTarget = ContactNormalizer.normalize(channel, target);
        String scopeHash = scopeHash(channel, purpose, normalizedTarget);
        VerificationCodeStore.ConsumeResult result = store.consume(
                scopeHash,
                hmac(scopeHash + "|" + code)
        );
        return switch (result) {
            case CONSUMED -> normalizedTarget;
            case EXPIRED -> throw new BusinessException(
                    ApiErrorCodes.VERIFICATION_CODE_EXPIRED,
                    "验证码已失效，请您重新获取"
            );
            case INVALID -> throw invalidCode();
            case LOCKED -> throw new BusinessException(
                    ApiErrorCodes.VERIFICATION_CODE_LOCKED,
                    "验证码错误次数过多，请您重新获取"
            );
        };
    }

    private String scopeHash(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String normalizedTarget
    ) {
        return hmac(channel.name() + "|" + purpose.name() + "|" + normalizedTarget);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getVerification().getPepper().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            ));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", impossible);
        }
    }

    private String safeClientAddress(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        String normalized = clientAddress.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private void validateConfiguration() {
        AuthProperties.Verification verification = properties.getVerification();
        if (verification.getPepper() == null || verification.getPepper().length() < 32) {
            throw new IllegalStateException("LUCKYFLOW_VERIFICATION_PEPPER must contain at least 32 characters");
        }
        if (verification.getCodeTtl() == null || verification.getCodeTtl().isNegative()
                || verification.getCodeTtl().isZero()
                || verification.getSendInterval() == null || verification.getSendInterval().isNegative()
                || verification.getSendInterval().isZero()
                || verification.getMaxAttempts() <= 0
                || verification.getHourlyLimit() <= 0
                || verification.getDailyLimit() < verification.getHourlyLimit()
                || verification.getIpHourlyLimit() <= 0) {
            throw new IllegalStateException("Invalid verification-code limits");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ApiErrorCodes.VALIDATION_FAILED, message);
    }

    private BusinessException invalidCode() {
        return new BusinessException(
                ApiErrorCodes.VERIFICATION_CODE_INVALID,
                "验证码不正确，请您重新输入"
        );
    }
}
