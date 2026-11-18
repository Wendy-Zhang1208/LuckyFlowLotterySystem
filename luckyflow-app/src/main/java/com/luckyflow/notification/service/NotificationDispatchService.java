package com.luckyflow.notification.service;

/** 连接短事务领取和事务外异步发送。 */
public interface NotificationDispatchService {

    /** 领取一批通知并提交到有界发送线程池，返回本次领取数量。 */
    int dispatchBatch(int batchSize);
}
