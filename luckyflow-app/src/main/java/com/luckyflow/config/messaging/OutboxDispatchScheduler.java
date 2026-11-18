package com.luckyflow.config.messaging;

import com.luckyflow.lottery.service.Interface.OutboxRecoveryService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 自动领取 Outbox 待发布事件，并收敛 Broker 结果长期未知的 attempt。 */
@Component
@ConditionalOnProperty(prefix = "luckyflow.outbox", name = "scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class OutboxDispatchScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatchScheduler.class);

    private final OutboxPublisherService publisher;
    private final OutboxRecoveryService recovery;
    private final int batchSize;
    private final Duration processingTimeout;

    public OutboxDispatchScheduler(
            OutboxPublisherService publisher,
            OutboxRecoveryService recovery,
            @Value("${luckyflow.outbox.batch-size:50}") int batchSize,
            @Value("${luckyflow.outbox.processing-timeout:PT2M}") Duration processingTimeout
    ) {
        if (batchSize < 1 || batchSize > 200 || processingTimeout == null
                || processingTimeout.compareTo(Duration.ofSeconds(30)) < 0) {
            throw new IllegalArgumentException("Invalid Outbox scheduler configuration");
        }
        this.publisher = publisher;
        this.recovery = recovery;
        this.batchSize = batchSize;
        this.processingTimeout = processingTimeout;
    }

    @Scheduled(fixedDelayString = "${luckyflow.outbox.publish-fixed-delay-millis:3000}",
            scheduler = OutboxSchedulingConfiguration.OUTBOX_TASK_SCHEDULER)
    public void publish() {
        try {
            // claim 独立短事务提交后才发往 RabbitMQ；本调度方法不包裹网络 I/O 事务。
            LOGGER.debug("Outbox publish scan started");
            int claimed = publisher.publishBatch(batchSize);
            LOGGER.debug("Outbox publish scan finished, claimed={}", claimed);
        } catch (RuntimeException failure) {
            LOGGER.error("Outbox publish scan failed", failure);
        }
    }

    @Scheduled(fixedDelayString = "${luckyflow.outbox.recovery-fixed-delay-millis:30000}",
            scheduler = OutboxSchedulingConfiguration.OUTBOX_TASK_SCHEDULER)
    public void recover() {
        try {
            LOGGER.debug("Outbox recovery scan started, processingTimeout={}", processingTimeout);
            int recovered = recovery.recoverTimedOut(processingTimeout, batchSize);
            LOGGER.debug("Outbox recovery scan finished, recovered={}", recovered);
        } catch (RuntimeException failure) {
            LOGGER.error("Outbox timeout recovery scan failed", failure);
        }
    }
}
