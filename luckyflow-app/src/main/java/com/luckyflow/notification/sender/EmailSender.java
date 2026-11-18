package com.luckyflow.notification.sender;

/** 邮件发送端口；正常返回仅表示 SMTP 服务端已经接受请求，不代表用户最终收到邮件。 */
public interface EmailSender {

    void send(NotificationSendCommand command);
}
