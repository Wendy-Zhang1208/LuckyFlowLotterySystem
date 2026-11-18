package com.luckyflow.auth.model;

/** 验证码只能用于签发时绑定的业务目的。 */
public enum VerificationPurpose {
    REGISTER,
    LOGIN,
    BIND,
    PASSWORD_RESET
}
