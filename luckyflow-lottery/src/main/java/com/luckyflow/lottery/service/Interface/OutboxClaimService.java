package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.entity.OutboxEvent;

import java.util.List;

/** 在短 MySQL 事务内领取一批当前可发送的 Outbox 事件。 */
public interface OutboxClaimService {

    /**
     * 锁定并将一批待处理事件迁移为处理中状态。
     *
     * @param batchSize 本次最多领取的事件数量，必须大于 0
     * @return 已成功迁移为处理中状态的事件；没有可处理事件时返回空列表
     */
    List<OutboxEvent> claimBatch(int batchSize);
}
