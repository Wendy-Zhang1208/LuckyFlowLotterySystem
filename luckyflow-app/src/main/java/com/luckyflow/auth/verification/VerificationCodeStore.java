package com.luckyflow.auth.verification;

/** 验证码的原子签发、消费和发送失败撤销端口。 */
public interface VerificationCodeStore {

    IssueResult issue(String scopeHash, String codeHash, String clientHash);

    ConsumeResult consume(String scopeHash, String suppliedCodeHash);

    void revoke(String scopeHash, String codeHash);

    enum IssueResult {
        ISSUED,
        SEND_INTERVAL,
        TARGET_HOURLY_LIMIT,
        TARGET_DAILY_LIMIT,
        IP_HOURLY_LIMIT
    }

    enum ConsumeResult {
        CONSUMED,
        EXPIRED,
        INVALID,
        LOCKED
    }
}
