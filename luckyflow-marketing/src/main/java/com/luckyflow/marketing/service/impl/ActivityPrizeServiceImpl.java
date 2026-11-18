package com.luckyflow.marketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.marketing.entity.ActivityPrize;
import com.luckyflow.marketing.entity.Prize;
import com.luckyflow.marketing.mapper.ActivityPrizeMapper;
import com.luckyflow.marketing.service.ActivityPrizeService;
import com.luckyflow.marketing.service.ActivityService;
import com.luckyflow.marketing.service.PrizeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.luckyflow.marketing.constant.ActivityPrizeStatusConstants.ACTIVITY_PRIZE_STATUS_DISABLED;
import static com.luckyflow.marketing.constant.ActivityPrizeStatusConstants.ACTIVITY_PRIZE_STATUS_ENABLED;
import static com.luckyflow.marketing.constant.PrizeStatusConstants.PRIZE_STATUS_ENABLED;
import static com.luckyflow.marketing.constant.PrizeTypeConstants.PHYSICAL;
import static com.luckyflow.marketing.constant.PrizeTypeConstants.POINTS;
import static com.luckyflow.marketing.constant.PrizeTypeConstants.VIRTUAL;

/**
 * 活动奖品领域服务实现，封装 Marketing 模块内部的数据访问与原子库存扣减。
 */
@Service
public class ActivityPrizeServiceImpl implements ActivityPrizeService {

    private final ActivityPrizeMapper activityPrizeMapper;
    private final ActivityService activityService;
    private final PrizeService prizeService;
    private final Clock clock;

    public ActivityPrizeServiceImpl(
            ActivityPrizeMapper activityPrizeMapper,
            ActivityService activityService,
            PrizeService prizeService,
            Clock clock
    ) {
        this.activityPrizeMapper = activityPrizeMapper;
        this.activityService = activityService;
        this.prizeService = prizeService;
        this.clock = clock;
    }

    @Override
    public List<ActivityPrize> findByActivityId(Long activityId) {
        return activityPrizeMapper.selectList(
                Wrappers.<ActivityPrize>lambdaQuery()
                        .eq(ActivityPrize::getActivityId, activityId)
                        .orderByAsc(ActivityPrize::getId)
        );
    }

    @Override
    public ActivityPrize findActivityPrize(Long activityPrizeId) {
        return activityPrizeMapper.selectById(activityPrizeId);
    }

    @Override
    public List<ActivityPrize> findEnabledByActivityId(Long activityId) {
        return activityPrizeMapper.selectList(
                Wrappers.<ActivityPrize>lambdaQuery()
                        .eq(ActivityPrize::getActivityId, activityId)
                        .eq(ActivityPrize::getStatus, ACTIVITY_PRIZE_STATUS_ENABLED)
                        .orderByAsc(ActivityPrize::getId)
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public ActivityPrize createActivityPrize(ActivityPrize activityPrize) {
        if (activityPrize != null) {
            // 配置阶段尚未消耗，剩余库存由本次分配量确定，不接受调用方另行指定。
            activityPrize.setRemainingInventory(activityPrize.getAllocatedInventory());
        }
        validateActivityPrizeConfiguration(activityPrize);
        if (activityPrize.getId() != null) {
            throw new IllegalArgumentException("New activity prize ID must be null");
        }

        activityService.lockActivityForConfigurationEdit(activityPrize.getActivityId());
        prizeService.lockPrizeDefinition(activityPrize.getPrizeId());

        LocalDateTime now = LocalDateTime.now(clock);
        // 划拨目录库存和新增奖池共用当前事务，插入失败时划拨一并回滚。
        adjustPrizeInventory(activityPrize.getPrizeId(), activityPrize.getAllocatedInventory(), now);
        activityPrize.setPrizeTypeSnapshot(null);
        activityPrize.setPrizeValueSnapshot(null);
        activityPrize.setCreateTime(now);
        activityPrize.setUpdateTime(now);
        if (activityPrizeMapper.insert(activityPrize) != 1 || activityPrize.getId() == null) {
            throw new BusinessException("新增奖池配置失败");
        }
        return activityPrize;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void updateActivityPrizeConfiguration(Long activityId, ActivityPrize activityPrize) {
        requirePositiveId(activityId, "activityId");
        if (activityPrize != null) {
            activityPrize.setRemainingInventory(activityPrize.getAllocatedInventory());
        }
        validateActivityPrizeConfiguration(activityPrize);
        requirePositiveId(activityPrize.getId(), "activityPrizeId");
        if (!Objects.equals(activityId, activityPrize.getActivityId())) {
            throw new IllegalArgumentException("Activity prize does not belong to the specified activity");
        }

        // 统一先锁活动、再锁奖池和奖品，发布与编辑使用同一锁顺序，避免发布读取到混合配置。
        activityService.lockActivityForConfigurationEdit(activityId);
        ActivityPrize existing = activityPrizeMapper.selectByIdForUpdate(activityPrize.getId());
        validateExistingOwnership(existing, activityId);
        // 换奖品时同时锁定旧、新目录，按主键升序获取，避免两个换奖请求反向等待。
        for (Long prizeId : List.of(existing.getPrizeId(), activityPrize.getPrizeId())
                .stream().distinct().sorted().toList()) {
            prizeService.lockPrizeDefinition(prizeId);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (Objects.equals(existing.getPrizeId(), activityPrize.getPrizeId())) {
            // 差量必须基于锁定后的旧值；相同配置重复保存时差量为零，不重复扣目录库存。
            int delta = activityPrize.getAllocatedInventory() - existing.getAllocatedInventory();
            adjustPrizeInventory(activityPrize.getPrizeId(), delta, now);
        } else {
            adjustPrizeInventory(existing.getPrizeId(), -existing.getAllocatedInventory(), now);
            adjustPrizeInventory(activityPrize.getPrizeId(), activityPrize.getAllocatedInventory(), now);
        }
        activityPrize.setUpdateTime(now);
        if (activityPrizeMapper.updateConfiguration(activityId, activityPrize) != 1) {
            throw new BusinessException("更新奖池配置失败");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteActivityPrize(Long activityId, Long activityPrizeId) {
        requirePositiveId(activityId, "activityId");
        requirePositiveId(activityPrizeId, "activityPrizeId");

        activityService.lockActivityForConfigurationEdit(activityId);
        ActivityPrize existing = activityPrizeMapper.selectByIdForUpdate(activityPrizeId);
        validateExistingOwnership(existing, activityId);
        prizeService.lockPrizeDefinition(existing.getPrizeId());
        // 删除配置前归还原分配量；删除或外键检查失败时，归还操作随事务回滚。
        adjustPrizeInventory(existing.getPrizeId(), -existing.getAllocatedInventory(), LocalDateTime.now(clock));
        if (activityPrizeMapper.deleteConfiguration(activityId, activityPrizeId) != 1) {
            throw new BusinessException("删除奖池配置失败");
        }
    }

    private void adjustPrizeInventory(Long prizeId, int delta, LocalDateTime updateTime) {
        if (delta == 0) {
            return;
        }
        int affectedRows = delta > 0
                ? activityPrizeMapper.allocatePrizeInventory(prizeId, delta, updateTime)
                : activityPrizeMapper.releasePrizeInventory(prizeId, -delta, updateTime);
        if (affectedRows != 1) {
            throw new BusinessException(delta > 0
                    ? "奖品目录可用库存不足，无法分配给活动"
                    : "奖品库存归还失败，可用库存不能超过总库存");
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<ActivityPrize> freezePrizeSnapshotsForPublication(Long activityId) {
        requirePositiveId(activityId, "activityId");
        activityService.lockActivityForPublication(activityId);

        List<ActivityPrize> configurations =
                activityPrizeMapper.selectByActivityIdForUpdate(activityId);
        if (configurations == null || configurations.isEmpty()) {
            throw new BusinessException("Activity prize pool is empty");
        }
        configurations.forEach(this::validatePersistedActivityPrize);

        List<ActivityPrize> enabledConfigurations = configurations.stream()
                .filter(configuration -> Integer.valueOf(ACTIVITY_PRIZE_STATUS_ENABLED)
                        .equals(configuration.getStatus()))
                .toList();
        if (enabledConfigurations.isEmpty()) {
            throw new BusinessException("Activity prize pool is empty");
        }

        // 多活动发布可能共享 Prize，按主键固定加锁顺序，降低交叉发布的死锁概率。
        List<Long> prizeIds = enabledConfigurations.stream()
                .map(ActivityPrize::getPrizeId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Prize> lockedPrizes = new HashMap<>(prizeIds.size());
        for (Long prizeId : prizeIds) {
            lockedPrizes.put(prizeId, prizeService.lockPrizeDefinition(prizeId));
        }

        LocalDateTime freezeTime = LocalDateTime.now(clock);
        for (ActivityPrize activityPrize : enabledConfigurations) {
            Prize prize = lockedPrizes.get(activityPrize.getPrizeId());
            validatePrizeForPublication(prize);
            int affectedRows = activityPrizeMapper.freezePrizeSnapshot(
                    activityId,
                    activityPrize.getId(),
                    activityPrize.getPrizeId(),
                    prize.getType(),
                    prize.getValue(),
                    freezeTime
            );
            if (affectedRows != 1) {
                throw new IllegalStateException("Failed to freeze activity prize definition");
            }
            activityPrize.setPrizeTypeSnapshot(prize.getType());
            activityPrize.setPrizeValueSnapshot(prize.getValue());
            activityPrize.setUpdateTime(freezeTime);
        }
        return List.copyOf(enabledConfigurations);
    }

    @Override
    public boolean tryDeductInventory(Long activityPrizeId) {
        return activityPrizeMapper.deductInventory(activityPrizeId) == 1;
    }

    private void validateActivityPrizeConfiguration(ActivityPrize activityPrize) {
        if (activityPrize == null) {
            throw new IllegalArgumentException("Activity prize configuration must not be null");
        }
        requirePositiveId(activityPrize.getActivityId(), "activityId");
        requirePositiveId(activityPrize.getPrizeId(), "prizeId");
        if (activityPrize.getWeight() == null || activityPrize.getWeight() <= 0) {
            // 与 MySQL chk_activity_prize_weight 保持一致，避免非法配置落库时变成 500。
            throw new IllegalArgumentException("Activity prize weight must be positive");
        }
        if (activityPrize.getAllocatedInventory() == null
                || activityPrize.getAllocatedInventory() < 0
                || activityPrize.getRemainingInventory() == null
                || activityPrize.getRemainingInventory() < 0
                || activityPrize.getRemainingInventory() > activityPrize.getAllocatedInventory()) {
            throw new IllegalArgumentException("Invalid activity prize inventory configuration");
        }
        if (!isSupportedStatus(activityPrize.getStatus())) {
            throw new IllegalArgumentException("Unsupported activity prize status");
        }
    }

    private void validatePersistedActivityPrize(ActivityPrize activityPrize) {
        validateActivityPrizeConfiguration(activityPrize);
        requirePositiveId(activityPrize.getId(), "activityPrizeId");
    }

    private void validateExistingOwnership(ActivityPrize existing, Long activityId) {
        if (existing == null) {
            throw new BusinessException("Activity prize does not exist");
        }
        if (!Objects.equals(activityId, existing.getActivityId())) {
            throw new BusinessException("Activity prize does not belong to the specified activity");
        }
    }

    private void validatePrizeForPublication(Prize prize) {
        if (prize == null) {
            throw new BusinessException("Prize does not exist");
        }
        if (!Integer.valueOf(PRIZE_STATUS_ENABLED).equals(prize.getStatus())) {
            throw new BusinessException("Only enabled prizes can be published");
        }
        if (!isSupportedPrizeType(prize.getType())) {
            throw new BusinessException("Unsupported prize type");
        }
        if (Integer.valueOf(POINTS).equals(prize.getType())) {
            BigDecimal value = prize.getValue();
            if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
                throw new BusinessException("Point prize value must be a positive integer");
            }
        } else if (prize.getValue() != null && prize.getValue().signum() < 0) {
            throw new BusinessException("Prize value must not be negative");
        }
    }

    private boolean isSupportedStatus(Integer status) {
        return Integer.valueOf(ACTIVITY_PRIZE_STATUS_DISABLED).equals(status)
                || Integer.valueOf(ACTIVITY_PRIZE_STATUS_ENABLED).equals(status);
    }

    private boolean isSupportedPrizeType(Integer type) {
        return Integer.valueOf(PHYSICAL).equals(type)
                || Integer.valueOf(VIRTUAL).equals(type)
                || Integer.valueOf(POINTS).equals(type);
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
