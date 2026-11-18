package com.luckyflow.notification.service;

import com.luckyflow.notification.entity.NotificationRecord;

/** 在数据库领取事务提交后调用对应外部渠道并落发送结果。 */
public interface NotificationSendService {

    /** 执行一次已领取 attempt；明确成功或失败均通过状态服务原子落库。 */
    void send(NotificationRecord notificationRecord);
}
