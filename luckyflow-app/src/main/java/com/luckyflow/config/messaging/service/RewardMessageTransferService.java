package com.luckyflow.config.messaging.service;

import org.springframework.amqp.core.Message;

/** 将失败消息可靠转移到业务重试队列或最终 DLQ。 */
public interface RewardMessageTransferService {

    void transferToRetry(Message originalMessage, int nextRetryCount, Throwable failure);

    void transferToDeadLetter(Message originalMessage, int retryCount, Throwable failure);
}
