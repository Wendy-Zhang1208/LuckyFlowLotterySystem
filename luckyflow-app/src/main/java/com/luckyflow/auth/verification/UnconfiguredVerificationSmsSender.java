package com.luckyflow.auth.verification;

import com.luckyflow.auth.model.VerificationPurpose;

import java.time.Duration;

/** 未取得供应商资质和凭据时明确失败，绝不伪造短信发送成功。 */
public class UnconfiguredVerificationSmsSender implements VerificationSmsSender {

    @Override
    public void send(String phone, String code, VerificationPurpose purpose, Duration validity) {
        throw new IllegalStateException("SMS verification provider is not configured");
    }
}
