package com.luckyflow.notification.sender.impl;

import com.luckyflow.notification.config.NotificationProperties;
import com.luckyflow.notification.sender.EmailSender;
import com.luckyflow.notification.sender.NotificationSendCommand;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 使用 Spring Mail 将中奖邮件提交给配置的 SMTP 服务。 */
@Component
public class SpringMailEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final NotificationProperties properties;

    public SpringMailEmailSender(
            JavaMailSender javaMailSender,
            NotificationProperties properties
    ) {
        this.javaMailSender = javaMailSender;
        this.properties = properties;
    }

    /** 方法正常返回仅证明 SMTP 接受了请求，不代表邮件已经实际送达收件箱。 */
    @Override
    public void send(NotificationSendCommand command) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmailFrom());
        message.setTo(command.recipient());
        message.setSubject("LuckyFlow 中奖通知");
        message.setText(
                "恭喜您在 LuckyFlow 活动中中奖，中奖记录编号："
                        + command.winningRecordId()
                        + "。后续奖励将按活动规则处理。"
        );
        javaMailSender.send(message);
    }
}
