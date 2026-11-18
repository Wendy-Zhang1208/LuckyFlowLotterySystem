package com.luckyflow.user.constant;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;

/** 前台六款原创云朵头像的稳定键。 */
public final class AvatarKeyConstants {

    public static final List<String> VALUES = List.of(
            "CLOUD_STAR",
            "CLOUD_HEART",
            "CLOUD_WINK",
            "CLOUD_HEADPHONES",
            "CLOUD_GLASSES",
            "CLOUD_HAT"
    );

    private static final Set<String> VALUE_SET = Set.copyOf(VALUES);

    public static boolean isSupported(String avatarKey) {
        return avatarKey != null && VALUE_SET.contains(avatarKey);
    }

    public static String random(SecureRandom random) {
        return VALUES.get(random.nextInt(VALUES.size()));
    }

    private AvatarKeyConstants() {
    }
}
