package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.constant.OutboxEventStatusConstants;
import com.luckyflow.lottery.entity.OutboxEvent;
import com.luckyflow.lottery.mapper.OutboxEventMapper;
import com.luckyflow.lottery.service.Interface.OutboxClaimService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 基于数据库行锁领取 Outbox 事件。
 *
 * <p>该服务只完成数据库状态迁移；RabbitMQ 发布必须在本事务提交、行锁释放后执行。</p>
 */
@Service
public class OutboxClaimServiceImpl implements OutboxClaimService {

    private final OutboxEventMapper outboxEventMapper;
    private final Clock clock;

    public OutboxClaimServiceImpl(OutboxEventMapper outboxEventMapper, Clock clock) {
        this.outboxEventMapper = outboxEventMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OutboxEvent> claimBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }

        LocalDateTime processingTime = LocalDateTime.now(clock);
        List<OutboxEvent> events = outboxEventMapper.selectPendingForUpdate(
                processingTime,
                batchSize
        );
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        // 查询锁定与状态迁移必须处于同一事务；任一事件迁移失败都会触发整批回滚。
        for (OutboxEvent event : events) {
            // token 标识单条 Outbox 的本次 publish attempt，不能在 batch 内共享。
            String processingToken = UUID.randomUUID().toString();
            int affectedRows = outboxEventMapper.markProcessing(
                    event.getId(),
                    processingToken,
                    processingTime
            );
            if (affectedRows != 1) {
                throw new IllegalStateException(
                        "Failed to claim outbox event, id=" + event.getId()
                                + ", affectedRows=" + affectedRows
                );
            }

            event.setStatus(OutboxEventStatusConstants.PROCESSING);
            event.setProcessingToken(processingToken);
            event.setProcessingTime(processingTime);
            event.setUpdatedTime(processingTime);
        }

        return events;
    }
}
