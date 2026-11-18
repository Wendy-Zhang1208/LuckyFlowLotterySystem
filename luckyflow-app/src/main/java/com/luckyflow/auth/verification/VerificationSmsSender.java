package com.luckyflow.auth.verification;

import com.luckyflow.auth.model.VerificationPurpose;

import java.time.Duration;

/** 真实短信供应商适配端口。 */
public interface VerificationSmsSender {

    void send(String phone, String code, VerificationPurpose purpose, Duration validity);
}
