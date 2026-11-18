package com.luckyflow.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 将持久化 intent、发布事务及 Redis 对账串联；崩溃时由定时恢复继续收敛。 */
@Service
public class ActivityOpeningApplicationService {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityOpeningApplicationService.class);
    private final ActivityPublicationIntentService intentService;
    private final ActivityPublicationTransactionService transactionService;

    public ActivityOpeningApplicationService(ActivityPublicationIntentService intentService,
            ActivityPublicationTransactionService transactionService) {
        this.intentService = intentService;
        this.transactionService = transactionService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishActivity(Long activityId) {
        String token = intentService.begin(activityId);
        try {
            transactionService.publish(activityId, token);
        } catch (RuntimeException originalFailure) {
            // 事务结果若未知，必须先按 MySQL 最终状态对账，不能直接删除 Redis 库存。
            try { intentService.reconcileOne(activityId, Duration.ZERO); }
            catch (RuntimeException recoveryFailure) {
                originalFailure.addSuppressed(recoveryFailure);
                LOG.error("Activity publication recovery deferred, activityId={}",
                        activityId, recoveryFailure);
            }
            throw originalFailure;
        }
        try { intentService.reconcileOne(activityId, Duration.ZERO); }
        catch (RuntimeException recoveryFailure) {
            // MySQL 已正式发布；归属标记待定时恢复，不能把成功发布伪装成失败让运营重试。
            LOG.error("Published activity awaits intent cleanup, activityId={}",
                    activityId, recoveryFailure);
        }
    }
}
