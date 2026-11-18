package com.luckyflow.auth.verification;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;
import com.luckyflow.notification.config.NotificationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 生产验证码发送器；邮件和短信均调用真实外部适配，不记录验证码或收件人。 */
@Component
public class ProviderVerificationMessageSender implements VerificationMessageSender {

    private final JavaMailSender mailSender;
    private final VerificationSmsSender smsSender;
    private final NotificationProperties notificationProperties;

    public ProviderVerificationMessageSender(
            JavaMailSender mailSender,
            VerificationSmsSender smsSender,
            NotificationProperties notificationProperties
    ) {
        this.mailSender = mailSender;
        this.smsSender = smsSender;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public void send(
            VerificationChannel channel,
            VerificationPurpose purpose,
            String normalizedTarget,
            String code,
            Duration validity
    ) {
        if (channel == VerificationChannel.EMAIL) {
            sendEmail(normalizedTarget, code, purpose, validity);
            return;
        }
        smsSender.send(normalizedTarget, code, purpose, validity);
    }

    private void sendEmail(
            String email,
            String code,
            VerificationPurpose purpose,
            Duration validity
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(notificationProperties.getEmailFrom());
        message.setTo(email);
        message.setSubject("LuckyFlow 验证码");
        message.setText("您正在进行 LuckyFlow " + purposeText(purpose)
                + "，验证码为：" + code + "。验证码在 "
                + validity.toMinutes() + " 分钟内有效，请勿转发给他人。");
        mailSender.send(message);
    }

    private String purposeText(VerificationPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "注册";
            case LOGIN -> "登录";
            case BIND -> "联系方式验证";
            case PASSWORD_RESET -> "密码重置";
        };
    }
}
