package com.luckyflow.auth.verification;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;

import java.time.Duration;

/** 验证码专用发送端口，不复用绑定中奖记录的通知命令。 */
public interface VerificationMessageSender {

    void send(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String normalizedTarget,
            String code,
            Duration validity
    );
}
