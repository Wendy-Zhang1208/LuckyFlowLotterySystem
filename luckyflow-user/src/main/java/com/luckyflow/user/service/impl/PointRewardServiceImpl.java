package com.luckyflow.user.service.impl;

import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.entity.PointTransaction;
import com.luckyflow.user.mapper.PointAccountMapper;
import com.luckyflow.user.mapper.PointTransactionMapper;
import com.luckyflow.user.service.PointRewardService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

import static com.luckyflow.user.constant.PointTransactionBizTypeConstants.POINT_TRANSACTION_BIZ_TYPE_LOTTERY_REWARD;

/** 中奖积分奖励服务实现，负责账户余额与积分流水在调用方事务内原子提交。 */
@Service
public class PointRewardServiceImpl implements PointRewardService {

    private final PointAccountMapper pointAccountMapper;
    private final PointTransactionMapper pointTransactionMapper;

    public PointRewardServiceImpl(
            PointAccountMapper pointAccountMapper,
            PointTransactionMapper pointTransactionMapper
    ) {
        this.pointAccountMapper = pointAccountMapper;
        this.pointTransactionMapper = pointTransactionMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantLotteryReward(
            Long winningRecordId,
            Long userId,
            long points,
            LocalDateTime now
    ) {
        validateArguments(winningRecordId, userId, points, now);

        // 账户行锁将同一用户的不同积分奖励串行化，确保流水余额与真实入账顺序一致。
        PointAccount pointAccount = pointAccountMapper.selectByUserIdForUpdate(userId);
        if (pointAccount == null || pointAccount.getBalance() == null) {
            throw new IllegalStateException("Point account does not exist");
        }

        long beforeBalance = pointAccount.getBalance();
        long afterBalance;
        try {
            afterBalance = Math.addExact(beforeBalance, points);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("Point account balance overflow", overflow);
        }
        PointTransaction pointTransaction = buildPointTransaction(
                winningRecordId,
                userId,
                points,
                afterBalance,
                now
        );

        try {
            // 流水先于余额写入；这样重复中奖事实不会在识别幂等前再次增加账户余额。
            int affectedRows = pointTransactionMapper.insert(pointTransaction);
            if (affectedRows != 1) {
                throw new IllegalStateException("Failed to create lottery reward point transaction");
            }
            if (pointTransaction.getId() == null) {
                throw new IllegalStateException("Lottery reward point transaction ID was not generated");
            }
        } catch (DuplicateKeyException duplicateReward) {
            // 仅唯一键竞争属于幂等分支；其他数据库故障继续向外传播并回滚调用方事务。
            PointTransaction existingTransaction =
                    pointTransactionMapper.selectByBizTypeAndBizIdForShare(
                            POINT_TRANSACTION_BIZ_TYPE_LOTTERY_REWARD,
                            winningRecordId
                    );
            validateExistingTransaction(
                    existingTransaction,
                    winningRecordId,
                    userId,
                    points
            );
            return;
        }

        int affectedRows = pointAccountMapper.addPoints(
                pointAccount.getId(),
                beforeBalance,
                points,
                now
        );
        if (affectedRows != 1) {
            throw new IllegalStateException("Failed to add lottery reward points");
        }
    }

    private PointTransaction buildPointTransaction(
            Long winningRecordId,
            Long userId,
            long points,
            long afterBalance,
            LocalDateTime now
    ) {
        PointTransaction pointTransaction = new PointTransaction();
        pointTransaction.setUserId(userId);
        pointTransaction.setBizType(POINT_TRANSACTION_BIZ_TYPE_LOTTERY_REWARD);
        pointTransaction.setBizId(winningRecordId);
        pointTransaction.setChangeAmount(points);
        pointTransaction.setAfterBalance(afterBalance);
        pointTransaction.setCreateTime(now);
        return pointTransaction;
    }

    private void validateExistingTransaction(
            PointTransaction existingTransaction,
            Long winningRecordId,
            Long userId,
            long points
    ) {
        if (existingTransaction == null) {
            throw new IllegalStateException(
                    "Duplicate lottery reward was reported but the existing transaction was not found"
            );
        }
        if (!Objects.equals(existingTransaction.getUserId(), userId)
                || !Objects.equals(
                        existingTransaction.getBizType(),
                        POINT_TRANSACTION_BIZ_TYPE_LOTTERY_REWARD
                )
                || !Objects.equals(existingTransaction.getBizId(), winningRecordId)
                || !Objects.equals(existingTransaction.getChangeAmount(), points)) {
            throw new IllegalStateException(
                    "Existing lottery reward point transaction conflicts with the current winning fact"
            );
        }
    }

    private void validateArguments(
            Long winningRecordId,
            Long userId,
            long points,
            LocalDateTime now
    ) {
        if (winningRecordId == null || winningRecordId <= 0) {
            throw new IllegalArgumentException("Winning record ID must be positive");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        if (points <= 0) {
            throw new IllegalArgumentException("Lottery reward points must be positive");
        }
        Objects.requireNonNull(now, "Reward time must not be null");
    }
}
