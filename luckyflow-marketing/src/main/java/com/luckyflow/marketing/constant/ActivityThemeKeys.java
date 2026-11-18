package com.luckyflow.marketing.constant;

import java.util.Set;

/** 前端已实现并由服务端共同约束的活动主题白名单。 */
public final class ActivityThemeKeys {

    public static final String DEFAULT = "DEFAULT";

    private static final Set<String> SUPPORTED = Set.of(
            DEFAULT,
            "SPRING",
            "SUMMER",
            "AUTUMN",
            "WINTER",
            "DIGITAL",
            "FOOD",
            "NEW_YEAR",
            "SPRING_FESTIVAL",
            "VALENTINES_DAY",
            "MID_AUTUMN",
            "NATIONAL_DAY",
            "CHRISTMAS"
    );

    private ActivityThemeKeys() {
    }

    public static boolean isSupported(String themeKey) {
        return themeKey != null && SUPPORTED.contains(themeKey);
    }

    /** 空值使用契约默认主题；非空值必须精确匹配白名单。 */
    public static String normalize(String themeKey) {
        if (themeKey == null || themeKey.isBlank()) {
            return DEFAULT;
        }
        String normalized = themeKey.trim().toUpperCase();
        if (!isSupported(normalized)) {
            throw new IllegalArgumentException("Unsupported activity themeKey");
        }
        return normalized;
    }
}
