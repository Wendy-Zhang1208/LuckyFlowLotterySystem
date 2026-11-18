package com.luckyflow.notification.sender;

import java.time.Duration;

/** 短信供应商端口，具体实现必须遵守调用方传入的有限超时时间。 */
public interface SmsSender {

    void send(NotificationSendCommand command, Duration timeout);
}
