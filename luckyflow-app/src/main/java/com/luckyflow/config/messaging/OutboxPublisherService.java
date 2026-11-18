package com.luckyflow.config.messaging;

/** Outbox 事件的 RabbitMQ 批量发布入口。 */
public interface OutboxPublisherService {

    /**
     * claim 一批可发送事件并逐条发起异步发布。
     *
     * @param batchSize 本次最多 claim 的事件数量
     * @return 本次成功 claim 的事件数量，不代表 Broker 已经 Confirm 成功
     */
    int publishBatch(int batchSize);
}
