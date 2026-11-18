package com.luckyflow.notification.sender.impl;

import com.luckyflow.notification.sender.NotificationSendCommand;
import com.luckyflow.notification.sender.SmsSender;

import java.time.Duration;

/** 本轮尚无短信供应商联调时的显式失败实现。 */
public class UnconfiguredSmsSender implements SmsSender {

    @Override
    public void send(NotificationSendCommand command, Duration timeout) {
        throw new IllegalStateException("SMS provider is not configured");
    }
}
