package com.luckyflow.config.messaging.service.impl;

import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.luckyflow.config.messaging.entity.ConsumedEvent;
import com.luckyflow.config.messaging.mapper.ConsumedEventMapper;
import com.luckyflow.config.messaging.service.ConsumedEventService;

/** 基于 consumed_event 唯一键实现消费幂等登记。 */
@Service
public class ConsumedEventServiceImpl implements ConsumedEventService {

    private final ConsumedEventMapper consumedEventMapper;

    public ConsumedEventServiceImpl(ConsumedEventMapper consumedEventMapper) {
        this.consumedEventMapper = consumedEventMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean tryConsume(
            String consumerName,
            Long eventId,
            LocalDateTime consumeTime
    ) {
        ConsumedEvent consumedEvent = new ConsumedEvent();
        consumedEvent.setConsumerName(consumerName);
        consumedEvent.setEventId(eventId);
        consumedEvent.setConsumeTime(consumeTime);

        try {
            // 直接 INSERT 由唯一键原子裁决并发竞争，避免“先查再写”的竞态窗口。
            int affectedRows = consumedEventMapper.insert(consumedEvent);
            if (affectedRows != 1) {
                throw new IllegalStateException("Failed to create consumed event record");
            }
            return true;
        } catch (DuplicateKeyException duplicateConsumption) {
            return false;
        }
    }
}
