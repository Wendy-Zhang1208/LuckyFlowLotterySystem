package com.luckyflow.publicapi;

/** HTTP DTO 的十进制字符串主键解析器，避免浏览器 Number 精度损失。 */
public final class HttpIdParser {

    private HttpIdParser() {
    }

    public static long requirePositiveLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a positive decimal string", exception);
        }
    }
}
