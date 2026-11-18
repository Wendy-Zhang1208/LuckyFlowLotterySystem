package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.constant.OutboxEventStatusConstants;
import com.luckyflow.lottery.constant.OutboxEventTypeConstants;
import com.luckyflow.lottery.entity.OutboxEvent;
import com.luckyflow.lottery.mapper.OutboxEventMapper;
import com.luckyflow.lottery.model.LotterySuccessEvent;
import com.luckyflow.lottery.service.Interface.OutboxEventWriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 在业务事务内创建待发布的中奖 Outbox 事件。
 */
@Service
public class OutboxEventWriteServiceImpl implements OutboxEventWriteService {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventWriteServiceImpl(
            OutboxEventMapper outboxEventMapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void writeLotterySuccessEvent(LotterySuccessEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Lottery success event must not be null");
        }
        if (event.winningRecordId() == null) {
            throw new IllegalArgumentException("Winning record ID must not be null");
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize lottery success event", exception);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType(OutboxEventTypeConstants.LOTTERY_SUCCESS);
        outboxEvent.setAggregateId(event.winningRecordId());
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxEventStatusConstants.PENDING);
        outboxEvent.setRetryCount(0);
        // PENDING 事件从事务提交后即可被 Claim；运行期处理字段必须保持为空。
        outboxEvent.setNextRetryTime(now);
        outboxEvent.setProcessingTime(null);
        outboxEvent.setProcessingToken(null);
        outboxEvent.setPublishTime(null);
        outboxEvent.setLastError(null);
        outboxEvent.setCreatedTime(now);
        outboxEvent.setUpdatedTime(now);

        if (outboxEventMapper.insert(outboxEvent) != 1 || outboxEvent.getId() == null) {
            throw new IllegalStateException("Failed to create lottery success outbox event");
        }
    }
}
