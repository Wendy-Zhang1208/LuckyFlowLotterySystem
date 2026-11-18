package com.luckyflow.config.messaging;

/** 目标重试队列或 DLQ 尚未得到可靠确认，原消息必须保持未 ACK。 */
public class RewardMessageTransferException extends RuntimeException {

    public RewardMessageTransferException(String message) {
        super(message);
    }

    public RewardMessageTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
