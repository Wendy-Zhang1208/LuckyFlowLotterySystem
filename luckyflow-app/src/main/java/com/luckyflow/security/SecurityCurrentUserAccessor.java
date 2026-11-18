package com.luckyflow.security;

import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 只从经过 Spring Security 验证的 JWT subject 读取用户标识。 */
@Component
public class SecurityCurrentUserAccessor implements CurrentUserAccessor {

    @Override
    public long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw unauthenticated();
        }
        try {
            long userId = Long.parseLong(authentication.getName());
            if (userId <= 0) {
                throw unauthenticated();
            }
            return userId;
        } catch (NumberFormatException invalidSubject) {
            throw unauthenticated();
        }
    }

    private BusinessException unauthenticated() {
        return new BusinessException(ApiErrorCodes.UNAUTHENTICATED, "请您先登录");
    }
}
