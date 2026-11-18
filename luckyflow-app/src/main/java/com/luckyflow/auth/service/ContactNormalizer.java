package com.luckyflow.auth.service;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/** 将唯一索引、验证码键和登录查询统一到同一种联系方式表示。 */
public final class ContactNormalizer {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE = Pattern.compile("^\\+?[1-9]\\d{7,14}$");

    public static String normalize(VerificationChannel channel, String target) {
        if (channel == null || target == null || target.isBlank()) {
            throw invalid("请您提供有效的验证渠道和联系方式");
        }
        return switch (channel) {
            case EMAIL -> normalizeEmail(target);
            case SMS -> normalizePhone(target);
        };
    }

    private static String normalizeEmail(String target) {
        String normalized = target.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !EMAIL.matcher(normalized).matches()) {
            throw invalid("请您输入有效的邮箱地址");
        }
        return normalized;
    }

    private static String normalizePhone(String target) {
        String normalized = target.trim().replaceAll("[\\s()-]", "");
        if (!PHONE.matcher(normalized).matches()) {
            throw invalid("请您输入有效的手机号");
        }
        return normalized;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ApiErrorCodes.VALIDATION_FAILED, message);
    }

    private ContactNormalizer() {
    }
}
