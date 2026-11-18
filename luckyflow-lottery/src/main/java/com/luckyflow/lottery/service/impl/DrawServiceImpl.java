package com.luckyflow.lottery.service.impl;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.lottery.entity.DrawRecord;
import com.luckyflow.lottery.entity.DrawResourceReservation;
import com.luckyflow.lottery.entity.WinningRecord;
import com.luckyflow.lottery.mapper.DrawRecordMapper;
import com.luckyflow.lottery.mapper.WinningRecordMapper;
import com.luckyflow.lottery.model.DailyDrawReservation;
import com.luckyflow.lottery.model.DrawCandidate;
import com.luckyflow.lottery.model.DrawRequestClaim;
import com.luckyflow.lottery.model.DrawResult;
import com.luckyflow.lottery.model.LotterySuccessEvent;
import com.luckyflow.lottery.model.PrizeInventoryReservation;
import com.luckyflow.lottery.service.Interface.DailyDrawLimitService;
import com.luckyflow.lottery.service.Interface.DrawRequestRecoveryService;
import com.luckyflow.lottery.service.Interface.DrawRequestService;
import com.luckyflow.lottery.service.Interface.DrawResourceReservationService;
import com.luckyflow.lottery.service.Interface.DrawService;
import com.luckyflow.lottery.service.Interface.OutboxEventWriteService;
import com.luckyflow.lottery.service.Interface.PrizeInventoryService;
import com.luckyflow.lottery.strategy.DrawStrategy;
import com.luckyflow.marketing.entity.Activity;
import com.luckyflow.marketing.entity.ActivityPrize;
import com.luckyflow.marketing.entity.Prize;
import com.luckyflow.marketing.service.ActivityPrizeService;
import com.luckyflow.marketing.service.ActivityService;
import com.luckyflow.marketing.service.PrizeService;
import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.entity.PointTransaction;
import com.luckyflow.user.service.PointAccountService;
import com.luckyflow.user.service.PointTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.luckyflow.lottery.constant.DrawResultStatusConstants.DRAW_RESULT_NO_WIN;
import static com.luckyflow.lottery.constant.DrawResultStatusConstants.DRAW_RESULT_WIN;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_RUNNING;
import static com.luckyflow.user.constant.PointTransactionBizTypeConstants.POINT_TRANSACTION_BIZ_TYPE_DRAW;

/**
 * 抽奖流程编排器。
 *
 * <p>请求登记、每个 Redis 意图和最终 MySQL 业务事务采用相互独立的短边界。这样既能在 Redis
 * 调用前留下可恢复证据，又避免主事务持有 {@code draw_request} 行锁时嵌套
 * {@code REQUIRES_NEW} 导致连接池耗尽或自等待。最终事务会再次锁定请求并校验执行 token。</p>
 */
@Service
public class DrawServiceImpl implements DrawService {

    private static final int MAX_PRIZE_SELECTION_ATTEMPTS = 2;

    private final ActivityService activityService;
    private final ActivityPrizeService activityPrizeService;
    private final PrizeService prizeService;
    private final PrizeInventoryService prizeInventoryService;
    private final DrawRecordMapper drawRecordMapper;
    private final WinningRecordMapper winningRecordMapper;
    private final PointAccountService pointAccountService;
    private final PointTransactionService pointTransactionService;
    private final DrawStrategy drawStrategy;
    private final DailyDrawLimitService dailyDrawLimitService;
    private final OutboxEventWriteService outboxEventWriteService;
    private final DrawRequestService drawRequestService;
    private final DrawResourceReservationService reservationService;
    private final DrawRequestRecoveryService recoveryService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public DrawServiceImpl(
            ActivityService activityService,
            ActivityPrizeService activityPrizeService,
            PrizeService prizeService,
            PrizeInventoryService prizeInventoryService,
            DrawRecordMapper drawRecordMapper,
            WinningRecordMapper winningRecordMapper,
            PointAccountService pointAccountService,
            PointTransactionService pointTransactionService,
            DrawStrategy drawStrategy,
            DailyDrawLimitService dailyDrawLimitService,
            OutboxEventWriteService outboxEventWriteService,
            DrawRequestService drawRequestService,
            DrawResourceReservationService reservationService,
            DrawRequestRecoveryService recoveryService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.activityService = activityService;
        this.activityPrizeService = activityPrizeService;
        this.prizeService = prizeService;
        this.prizeInventoryService = prizeInventoryService;
        this.drawRecordMapper = drawRecordMapper;
        this.winningRecordMapper = winningRecordMapper;
        this.pointAccountService = pointAccountService;
        this.pointTransactionService = pointTransactionService;
        this.drawStrategy = drawStrategy;
        this.dailyDrawLimitService = dailyDrawLimitService;
        this.outboxEventWriteService = outboxEventWriteService;
        this.drawRequestService = drawRequestService;
        this.reservationService = reservationService;
        this.recoveryService = recoveryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setName("draw-main-transaction");
        this.clock = clock;
    }

    @Override
    public DrawResult draw(String requestId, Long userId, Long activityId) {
        DrawRequestClaim claim = drawRequestService.registerAndClaim(
                requestId,
                userId,
                activityId
        );
        if (claim.alreadyCompleted()) {
            return claim.completedResult();
        }

        String executionToken = claim.executionToken();
        try {
            Activity activity = activityService.findActivity(activityId);
            validateActivity(activity);

            reserveDailyDrawLimit(
                    requestId,
                    executionToken,
                    userId,
                    activity
            );

            Integer pointCost = validatePointCost(activity.getPointCost());
            validatePointAvailability(userId, pointCost);
            List<ActivityPrize> activityPrizes =
                    activityPrizeService.findEnabledByActivityId(activityId);
            DrawCandidate winningCandidate = selectAndReservePrize(
                    requestId,
                    executionToken,
                    activity,
                    activityPrizes
            );

            DrawResult result = transactionTemplate.execute(status -> executeMainTransaction(
                    requestId,
                    executionToken,
                    userId,
                    activityId,
                    pointCost,
                    winningCandidate
            ));
            if (result == null) {
                throw new IllegalStateException("Draw transaction returned no result");
            }
            return result;
        } catch (RuntimeException drawFailure) {
            // 无论失败发生在 Redis 前后还是主事务内，统一以持久化请求/意图驱动补偿。
            // 回调与此处可能同时触发，但数据库状态迁移和 Lua 凭证均为幂等。
            try {
                recoveryService.cancelAndRecover(requestId, executionToken);
            } catch (RuntimeException recoveryFailure) {
                drawFailure.addSuppressed(recoveryFailure);
            }
            throw drawFailure;
        }
    }

    private DailyDrawReservation reserveDailyDrawLimit(
            String requestId,
            String executionToken,
            Long userId,
            Activity activity
    ) {
        DrawResourceReservation intent = reservationService.prepareDailyDrawLimit(
                requestId,
                executionToken,
                userId,
                activity.getId()
        );
        DailyDrawReservation reservation = new DailyDrawReservation(
                intent.getResourceKey(),
                intent.getReservationKey()
        );

        Optional<DailyDrawReservation> reserved = dailyDrawLimitService.tryReserve(
                reservation,
                activity.getDailyDrawLimit()
        );
        if (reserved.isEmpty()) {
            reservationService.markNotReserved(intent.getId());
            throw new BusinessException("Daily draw limit has been reached");
        }
        reservationService.markReserved(intent.getId());
        return reservation;
    }

    private DrawCandidate selectAndReservePrize(
            String requestId,
            String executionToken,
            Activity activity,
            List<ActivityPrize> activityPrizes
    ) {
        for (int attempt = 1; attempt <= MAX_PRIZE_SELECTION_ATTEMPTS; attempt++) {
            // 每次竞争失败后重新读取 Redis 快照并重新运行策略，仍然最多尝试两次。
            List<DrawCandidate> candidates = buildCandidates(activityPrizes);
            Optional<DrawCandidate> selected = drawStrategy.draw(
                    candidates,
                    activity.getNoWinWeight()
            );
            if (selected.isEmpty()) {
                return null;
            }

            DrawCandidate candidate = selected.orElseThrow();
            DrawResourceReservation intent = reservationService.preparePrizeInventory(
                    requestId,
                    executionToken,
                    attempt,
                    candidate.activityPrizeId()
            );
            PrizeInventoryReservation reservation = new PrizeInventoryReservation(
                    intent.getResourceKey(),
                    intent.getReservationKey()
            );
            Optional<PrizeInventoryReservation> reserved =
                    prizeInventoryService.tryReserve(reservation);

            if (reserved.isPresent()) {
                reservationService.markReserved(intent.getId());
                return candidate;
            }
            reservationService.markNotReserved(intent.getId());
        }
        return null;
    }

    private DrawResult executeMainTransaction(
            String requestId,
            String executionToken,
            Long userId,
            Long activityId,
            int pointCost,
            DrawCandidate winningCandidate
    ) {
        // 最终提交前重新锁定请求，恢复任务或重复调用一旦已取消/完成，本执行即不能继续。
        drawRequestService.lockForExecution(requestId, executionToken);
        registerCompletionRecovery(requestId, executionToken);

        Activity currentActivity = activityService.findActivity(activityId);
        validateActivity(currentActivity);
        if (validatePointCost(currentActivity.getPointCost()) != pointCost) {
            throw new BusinessException("Activity point cost changed during draw");
        }

        Long afterBalance = deductPointsIfNecessary(userId, pointCost);
        boolean won = winningCandidate != null;
        if (won && !activityPrizeService.tryDeductInventory(
                winningCandidate.activityPrizeId())) {
            throw new BusinessException("Prize inventory deduction failed");
        }

        LocalDateTime recordTime = LocalDateTime.now(clock);
        DrawRecord drawRecord = createDrawRecord(userId, activityId, won, recordTime);
        recordPointTransactionIfNecessary(
                userId,
                pointCost,
                afterBalance,
                drawRecord.getId(),
                recordTime
        );

        DrawResult result = won
                ? createWinningResult(
                        userId,
                        activityId,
                        winningCandidate,
                        drawRecord,
                        recordTime
                )
                : new DrawResult(drawRecord.getId(), false, null, null, null);

        // Redis 已成为正式消耗和请求 SUCCESS 必须与核心记录、WinningRecord、Outbox 同时提交。
        reservationService.confirmReserved(requestId);
        drawRequestService.markSuccess(requestId, executionToken, result);
        return result;
    }

    private Long deductPointsIfNecessary(Long userId, int pointCost) {
        if (pointCost == 0) {
            return null;
        }
        if (!pointAccountService.tryDeductPoints(userId, pointCost)) {
            throw new BusinessException("Insufficient points or point account does not exist");
        }
        PointAccount pointAccount = pointAccountService.findAccountByUserId(userId);
        if (pointAccount == null) {
            throw new BusinessException("Point account does not exist after deduction");
        }
        return pointAccount.getBalance();
    }

    private void validatePointAvailability(Long userId, int pointCost) {
        if (pointCost == 0) {
            return;
        }
        PointAccount pointAccount = pointAccountService.findAccountByUserId(userId);
        if (pointAccount == null
                || pointAccount.getBalance() == null
                || pointAccount.getBalance() < pointCost) {
            throw new BusinessException("Insufficient points or point account does not exist");
        }
    }

    private DrawRecord createDrawRecord(
            Long userId,
            Long activityId,
            boolean won,
            LocalDateTime recordTime
    ) {
        DrawRecord drawRecord = new DrawRecord();
        drawRecord.setUserId(userId);
        drawRecord.setActivityId(activityId);
        drawRecord.setResultStatus(won ? DRAW_RESULT_WIN : DRAW_RESULT_NO_WIN);
        drawRecord.setDrawTime(recordTime);
        drawRecord.setCreateTime(recordTime);
        if (drawRecordMapper.insert(drawRecord) != 1 || drawRecord.getId() == null) {
            throw new BusinessException("Draw record creation failed");
        }
        return drawRecord;
    }

    private void recordPointTransactionIfNecessary(
            Long userId,
            int pointCost,
            Long afterBalance,
            Long drawRecordId,
            LocalDateTime recordTime
    ) {
        if (pointCost == 0) {
            return;
        }
        PointTransaction pointTransaction = new PointTransaction();
        pointTransaction.setUserId(userId);
        pointTransaction.setBizType(POINT_TRANSACTION_BIZ_TYPE_DRAW);
        pointTransaction.setBizId(drawRecordId);
        pointTransaction.setChangeAmount(-(long) pointCost);
        pointTransaction.setAfterBalance(afterBalance);
        pointTransaction.setCreateTime(recordTime);
        if (!pointTransactionService.recordTransaction(pointTransaction)) {
            throw new BusinessException("Point transaction creation failed");
        }
    }

    private DrawResult createWinningResult(
            Long userId,
            Long activityId,
            DrawCandidate winningCandidate,
            DrawRecord drawRecord,
            LocalDateTime recordTime
    ) {
        ActivityPrize winningActivityPrize = activityPrizeService.findActivityPrize(
                winningCandidate.activityPrizeId()
        );
        if (winningActivityPrize == null
                || !activityId.equals(winningActivityPrize.getActivityId())
                || !winningCandidate.prizeId().equals(winningActivityPrize.getPrizeId())
                || winningActivityPrize.getPrizeTypeSnapshot() == null) {
            throw new BusinessException("Winning prize definition is not frozen");
        }
        Prize prize = prizeService.findPrize(winningCandidate.prizeId());
        if (prize == null) {
            throw new BusinessException("Winning prize does not exist");
        }

        WinningRecord winningRecord = new WinningRecord();
        winningRecord.setDrawRecordId(drawRecord.getId());
        winningRecord.setActivityPrizeId(winningCandidate.activityPrizeId());
        winningRecord.setWinTime(recordTime);
        winningRecord.setCreateTime(recordTime);
        if (winningRecordMapper.insert(winningRecord) != 1
                || winningRecord.getId() == null) {
            throw new BusinessException("Winning record creation failed");
        }

        // 已成立的中奖事实与 WinningRecord 在同一 MySQL 本地事务中写入 Outbox。
        outboxEventWriteService.writeLotterySuccessEvent(new LotterySuccessEvent(
                winningRecord.getId(),
                userId,
                activityId,
                winningCandidate.activityPrizeId(),
                prize.getId(),
                winningActivityPrize.getPrizeTypeSnapshot()
        ));

        return new DrawResult(
                drawRecord.getId(),
                true,
                prize.getId(),
                prize.getName(),
                winningActivityPrize.getPrizeTypeSnapshot()
        );
    }

    private void registerCompletionRecovery(String requestId, String executionToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Draw transaction synchronization is not active");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        recoveryService.handleTransactionCompletion(
                                requestId,
                                executionToken,
                                status
                        );
                    }
                }
        );
    }

    private int validatePointCost(Integer pointCost) {
        if (pointCost == null) {
            throw new BusinessException("Activity point cost must not be null");
        }
        if (pointCost < 0) {
            throw new BusinessException("Activity point cost must not be negative");
        }
        return pointCost;
    }

    private void validateActivity(Activity activity) {
        if (activity == null) {
            throw new BusinessException("Activity does not exist");
        }
        if (!Integer.valueOf(ACTIVITY_STATUS_RUNNING).equals(activity.getStatus())) {
            throw new BusinessException("Activity is not running");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (activity.getStartTime() == null
                || activity.getEndTime() == null
                || now.isBefore(activity.getStartTime())
                || !now.isBefore(activity.getEndTime())) {
            throw new BusinessException("Activity is outside its valid time range");
        }
    }

    private List<DrawCandidate> buildCandidates(List<ActivityPrize> activityPrizes) {
        List<Long> activityPrizeIds = activityPrizes.stream()
                .map(ActivityPrize::getId)
                .toList();
        Map<Long, Integer> inventorySnapshots =
                prizeInventoryService.getInventorySnapshots(activityPrizeIds);

        return activityPrizes.stream()
                .map(activityPrize -> {
                    Integer inventory = inventorySnapshots.get(activityPrize.getId());
                    if (inventory == null) {
                        throw new IllegalStateException(
                                "Prize inventory snapshot is missing: " + activityPrize.getId()
                        );
                    }
                    return new DrawCandidate(
                            activityPrize.getId(),
                            activityPrize.getPrizeId(),
                            activityPrize.getWeight(),
                            inventory
                    );
                })
                .toList();
    }
}
