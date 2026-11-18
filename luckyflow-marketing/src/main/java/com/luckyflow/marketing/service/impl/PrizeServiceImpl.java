package com.luckyflow.marketing.service.impl;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.marketing.entity.Prize;
import com.luckyflow.marketing.mapper.PrizeMapper;
import com.luckyflow.marketing.service.PrizeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import static com.luckyflow.marketing.constant.PrizeStatusConstants.PRIZE_STATUS_DISABLED;
import static com.luckyflow.marketing.constant.PrizeStatusConstants.PRIZE_STATUS_ENABLED;
import static com.luckyflow.marketing.constant.PrizeTypeConstants.PHYSICAL;
import static com.luckyflow.marketing.constant.PrizeTypeConstants.POINTS;
import static com.luckyflow.marketing.constant.PrizeTypeConstants.VIRTUAL;

/**
 * 奖品目录服务实现，负责将 PrizeMapper 限定在 Marketing 模块内部。
 */
@Service
public class PrizeServiceImpl implements PrizeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PrizeMapper prizeMapper;
    private final Clock clock;

    public PrizeServiceImpl(PrizeMapper prizeMapper, Clock clock) {
        this.prizeMapper = prizeMapper;
        this.clock = clock;
    }

    @Override
    public Prize findPrize(Long prizeId) {
        return prizeMapper.selectById(prizeId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Prize createPrize(Prize prize) {
        if (prize != null) {
            // 新目录尚无奖池分配，可用量等于总量，不接受请求单独覆盖可用库存。
            prize.setAvailableInventory(prize.getTotalInventory());
        }
        validatePrizeConfiguration(prize, false);
        if (prize.getId() != null) {
            throw new IllegalArgumentException("New prize ID must be null");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        prize.setCreateTime(now);
        prize.setUpdateTime(now);
        if (prizeMapper.insert(prize) != 1 || prize.getId() == null) {
            throw new IllegalStateException("Failed to create prize");
        }
        return prize;
    }

    @Override
    public PrizePage findPrizes(String keyword, Integer type, Integer status, int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "page must be positive and size must be between 1 and 100"
            );
        }
        if (type != null && !isSupportedType(type)) {
            throw new IllegalArgumentException("Unsupported prize type");
        }
        if (status != null && !isSupportedStatus(status)) {
            throw new IllegalArgumentException("Unsupported prize status");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        int offset = Math.multiplyExact(page - 1, size);
        return new PrizePage(
                prizeMapper.selectAdminPage(normalizedKeyword, type, status, offset, size),
                prizeMapper.countAdminPage(normalizedKeyword, type, status)
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Prize lockPrizeDefinition(Long prizeId) {
        requirePositiveId(prizeId, "prizeId");
        Prize prize = prizeMapper.selectByIdForUpdate(prizeId);
        if (prize == null) {
            throw new BusinessException("Prize does not exist");
        }
        return prize;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void updatePrizeConfiguration(Prize prize) {
        if (prize != null) {
            // 可用量是服务端派生字段；先按总量校验输入，再在持锁后扣除已分配总量。
            prize.setAvailableInventory(prize.getTotalInventory());
        }
        validatePrizeConfiguration(prize, true);
        lockAndRequireEditable(prize.getId());

        // 既有 READ_COMMITTED 事务在取得 Prize 锁后读取最新已提交分配，避免旧快照覆盖并发划拨。
        long allocatedInventory = prizeMapper.sumAllocatedInventory(prize.getId());
        if (allocatedInventory < 0 || allocatedInventory > prize.getTotalInventory()) {
            throw new BusinessException("奖品总库存不能小于已分配给活动的库存");
        }
        prize.setAvailableInventory((int) (prize.getTotalInventory() - allocatedInventory));
        prize.setUpdateTime(LocalDateTime.now(clock));
        if (prizeMapper.updateConfiguration(prize) != 1) {
            throw new BusinessException("更新奖品目录失败");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void deletePrize(Long prizeId) {
        lockAndRequireEditable(prizeId);
        if (prizeMapper.deleteById(prizeId) != 1) {
            throw new IllegalStateException("Failed to delete prize");
        }
    }

    private void lockAndRequireEditable(Long prizeId) {
        // 编辑先锁 Prize；发布先锁 Activity/ActivityPrize、后锁 Prize。
        // 引用检查不锁奖池行，避免两条路径形成反向等待；READ COMMITTED 确保等待 Prize 锁后看到新提交的快照。
        if (!Integer.valueOf(Isolation.READ_COMMITTED.value()).equals(
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalStateException("Prize editing requires a READ_COMMITTED transaction");
        }
        lockPrizeDefinition(prizeId);
        if (prizeMapper.hasPublishedReference(prizeId)) {
            throw new BusinessException("奖品已被正式发布的活动引用，请您创建新奖品");
        }
    }

    private void validatePrizeConfiguration(Prize prize, boolean requireId) {
        if (prize == null) {
            throw new IllegalArgumentException("Prize configuration must not be null");
        }
        if (requireId) {
            requirePositiveId(prize.getId(), "prizeId");
        }
        if (prize.getName() == null || prize.getName().isBlank()) {
            throw new IllegalArgumentException("Prize name must not be blank");
        }
        if (prize.getDescription() != null && prize.getDescription().length() > 1000) {
            throw new IllegalArgumentException("Prize description is too long");
        }
        if (prize.getIllustration() != null && prize.getIllustration().length() > 255) {
            throw new IllegalArgumentException("Prize illustration is too long");
        }
        if (!isSupportedType(prize.getType())) {
            throw new IllegalArgumentException("Unsupported prize type");
        }
        if (prize.getTotalInventory() == null || prize.getTotalInventory() < 0
                || prize.getAvailableInventory() == null
                || prize.getAvailableInventory() < 0
                || prize.getAvailableInventory() > prize.getTotalInventory()) {
            throw new IllegalArgumentException("Invalid prize inventory configuration");
        }
        if (!isSupportedStatus(prize.getStatus())) {
            throw new IllegalArgumentException("Unsupported prize status");
        }
        validatePrizeValue(prize.getType(), prize.getValue());
    }

    private void validatePrizeValue(Integer prizeType, BigDecimal value) {
        if (Integer.valueOf(POINTS).equals(prizeType)) {
            if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Point prize value must be a positive integer");
            }
            return;
        }
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException("Prize value must not be negative");
        }
    }

    private boolean isSupportedType(Integer type) {
        return Integer.valueOf(PHYSICAL).equals(type)
                || Integer.valueOf(VIRTUAL).equals(type)
                || Integer.valueOf(POINTS).equals(type);
    }

    private boolean isSupportedStatus(Integer status) {
        return Integer.valueOf(PRIZE_STATUS_DISABLED).equals(status)
                || Integer.valueOf(PRIZE_STATUS_ENABLED).equals(status);
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("keyword is too long");
        }
        return normalized;
    }
}
