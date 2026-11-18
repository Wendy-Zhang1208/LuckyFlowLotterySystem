package com.luckyflow.exception;

/**
 * HTTP 应用层可稳定识别的业务错误码。
 *
 * <p>领域内部仍可使用 {@link BusinessException} 的默认错误码；只有客户端必须区分的
 * 恢复、认证和权限语义才在这里分配专用值。</p>
 */
public final class ApiErrorCodes {

    public static final int VALIDATION_FAILED = 40000;
    public static final int UNAUTHENTICATED = 40100;
    public static final int INVALID_CREDENTIALS = 40101;
    public static final int VERIFICATION_CODE_INVALID = 40102;
    public static final int VERIFICATION_CODE_EXPIRED = 40103;
    public static final int VERIFICATION_CODE_LOCKED = 40104;
    public static final int FORBIDDEN = 40300;
    public static final int RESOURCE_NOT_FOUND = 40400;
    public static final int CONFLICT = 40900;
    public static final int CONTACT_ALREADY_REGISTERED = 40901;
    public static final int CONCURRENT_ACCOUNT_UPDATE = 40902;
    public static final int RATE_LIMITED = 42900;
    public static final int PASSWORD_NOT_SET = 42201;
    public static final int CURRENT_PASSWORD_INVALID = 42202;
    public static final int LAST_VERIFIED_CONTACT = 42203;
    public static final int CONTACT_NOT_OWNED = 42204;
    public static final int USER_DISABLED = 42205;
    public static final int VERIFICATION_PROVIDER_UNAVAILABLE = 50301;
    public static final int DRAW_REQUEST_PROCESSING = 42210;
    public static final int DRAW_REQUEST_CANCELLED = 42211;
    public static final int DRAW_RECOVERY_PENDING = 42212;

    private ApiErrorCodes() {
    }
}
