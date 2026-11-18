package com.luckyflow.service;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.lottery.service.Interface.PrizeInventoryPreparationService;
import com.luckyflow.marketing.constant.ActivityStatusConstants;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 发布意图先落 MySQL，再由独立事务核对 Redis 初始化结果。 */
@Service
public class ActivityPublicationIntentService {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityPublicationIntentService.class);
    private static final int ACTIVE = 0;
    private static final int ROLLBACK_CLEANUP = 1;
    private static final int COMPLETED = 2;
    private static final int CANCELLED = 3;
    private static final int COMMITTED_CLEANUP = 4;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PrizeInventoryPreparationService inventory;
    private final Clock clock;

    public ActivityPublicationIntentService(JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PrizeInventoryPreparationService inventory, Clock clock) {
        this.jdbc = jdbc;
        this.inventory = inventory;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public String begin(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("Invalid activityId");
        }
        String token = UUID.randomUUID().toString();
        transaction.executeWithoutResult(ignored -> {
            List<Integer> statuses = jdbc.query("SELECT status FROM activity WHERE id = ?",
                    (rs, row) -> rs.getInt(1), activityId);
            if (statuses.isEmpty() || statuses.get(0) !=
                    ActivityStatusConstants.ACTIVITY_STATUS_PENDING_REVIEW) {
                throw new BusinessException("只有待审核活动可以发布");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            jdbc.update("INSERT INTO activity_publication_intent "
                            + "(activity_id, attempt_token, status, created_time, updated_time) "
                            + "VALUES (?, ?, 0, ?, ?) ON DUPLICATE KEY UPDATE activity_id = activity_id",
                    activityId, token, now, now);
            Intent current = lockIntent(activityId);
            if (current.status == ACTIVE && current.token.equals(token)) {
                return;
            }
            if (current.status != COMPLETED && current.status != CANCELLED) {
                throw new BusinessException("活动发布意图仍在处理或恢复中，请您稍后重试");
            }
            jdbc.update("UPDATE activity_publication_intent SET attempt_token = ?, status = 0, "
                            + "created_time = ?, updated_time = ? WHERE activity_id = ?",
                    token, now, now, activityId);
        });
        return token;
    }

    /** 主发布事务取得 Activity 行锁后检查 intent，防止恢复已取消的 attempt 继续初始化库存。 */
    public void requireActive(Long activityId, String token) {
        Intent current = lockIntent(activityId);
        if (current == null || current.status != ACTIVE || !current.token.equals(token)) {
            throw new IllegalStateException("Publication attempt is no longer active");
        }
    }

    public int recoverBatch(Duration grace, int batchSize) {
        if (grace == null || grace.isNegative() || batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Invalid publication recovery configuration");
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(grace);
        List<Long> candidates = jdbc.query("SELECT activity_id FROM activity_publication_intent "
                        + "WHERE status IN (0, 1, 4) AND updated_time <= ? "
                        + "ORDER BY updated_time, activity_id LIMIT ?",
                (rs, row) -> rs.getLong(1), cutoff, batchSize);
        int recovered = 0;
        for (Long activityId : candidates) {
            try {
                if (reconcileOne(activityId, grace)) { recovered++; }
            } catch (RuntimeException failure) {
                LOG.error("Activity publication recovery failed, activityId={}", activityId, failure);
            }
        }
        return recovered;
    }

    public boolean reconcileOne(Long activityId, Duration grace) {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(grace);
        RecoveryAction action = transaction.execute(ignored -> {
            List<Integer> lockedActivity = jdbc.query(
                    "SELECT status FROM activity WHERE id = ? FOR UPDATE SKIP LOCKED",
                    (rs, row) -> rs.getInt(1), activityId);
            if (lockedActivity.isEmpty()) {
                // 空结果也可能表示另一发布事务持有行锁；存在性只读查询用于区分缺失行。
                List<Integer> existing = jdbc.query("SELECT status FROM activity WHERE id = ?",
                        (rs, row) -> rs.getInt(1), activityId);
                if (!existing.isEmpty()) { return null; }
            }
            Intent intent = lockIntent(activityId);
            if (intent == null || intent.status == COMPLETED || intent.status == CANCELLED
                    || intent.updatedTime.isAfter(cutoff)) {
                return null;
            }
            boolean published = !lockedActivity.isEmpty()
                    && lockedActivity.get(0) >= ActivityStatusConstants.ACTIVITY_STATUS_PUBLISHED;
            int cleanupStatus = published ? COMMITTED_CLEANUP : ROLLBACK_CLEANUP;
            if (intent.status != cleanupStatus) {
                jdbc.update("UPDATE activity_publication_intent SET status = ?, updated_time = ? "
                                + "WHERE activity_id = ? AND attempt_token = ? AND status = ?",
                        cleanupStatus, LocalDateTime.now(clock), activityId,
                        intent.token, intent.status);
            }
            return new RecoveryAction(intent.token, cleanupStatus);
        });
        if (action == null) { return false; }

        // Redis I/O 在 MySQL 短事务外执行；Lua 只触碰归属于当前 token 的 key，重复执行安全。
        inventory.cleanupOwned(activityId, action.token, action.cleanupStatus == ROLLBACK_CLEANUP);
        transaction.executeWithoutResult(ignored -> jdbc.update(
                "UPDATE activity_publication_intent SET status = ?, updated_time = ? "
                        + "WHERE activity_id = ? AND attempt_token = ? AND status = ?",
                action.cleanupStatus == ROLLBACK_CLEANUP ? CANCELLED : COMPLETED,
                LocalDateTime.now(clock), activityId, action.token, action.cleanupStatus));
        return true;
    }

    private Intent lockIntent(Long activityId) {
        List<Intent> rows = jdbc.query("SELECT attempt_token, status, updated_time "
                        + "FROM activity_publication_intent WHERE activity_id = ? FOR UPDATE",
                (rs, row) -> new Intent(rs.getString(1), rs.getInt(2),
                        rs.getTimestamp(3).toLocalDateTime()), activityId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record Intent(String token, int status, LocalDateTime updatedTime) { }
    private record RecoveryAction(String token, int cleanupStatus) { }
}
