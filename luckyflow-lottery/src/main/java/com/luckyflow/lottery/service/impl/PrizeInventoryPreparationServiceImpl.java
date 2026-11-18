package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.service.Interface.PrizeInventoryInitializer;
import com.luckyflow.lottery.service.Interface.PrizeInventoryPreparationService;
import com.luckyflow.marketing.entity.ActivityPrize;
import com.luckyflow.marketing.service.ActivityPrizeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 活动开放前的 Redis 奖池准备编排器。
 *
 * <p>该组件只负责低频准备流程，不参与用户抽奖请求。正式发布编排会先锁定 Activity 行，
 * 使同一活动的发布与配置编辑串行；本组件自身不额外持有分布式锁。</p>
 */
@Service
public class PrizeInventoryPreparationServiceImpl implements PrizeInventoryPreparationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        PrizeInventoryPreparationServiceImpl.class);

    private final ActivityPrizeService activityPrizeService;
    private final PrizeInventoryInitializer prizeInventoryInitializer;

    public PrizeInventoryPreparationServiceImpl(
            ActivityPrizeService activityPrizeService,
            PrizeInventoryInitializer prizeInventoryInitializer
    ) {
        this.activityPrizeService = activityPrizeService;
        this.prizeInventoryInitializer = prizeInventoryInitializer;
    }

    @Override
    public void prepareOwned(Long activityId, String attemptToken) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("Invalid activityId");
        }
        List<ActivityPrize> activityPrizes = activityPrizeService.findEnabledByActivityId(activityId);
        if (activityPrizes == null || activityPrizes.isEmpty()) {
            throw new IllegalStateException("Activity prize pool is empty");
        }
        for (ActivityPrize activityPrize : activityPrizes) {
            Integer remaining = activityPrize.getRemainingInventory();
            if (remaining == null) {
                throw new IllegalStateException("Activity prize remaining inventory must not be null");
            }
            prizeInventoryInitializer.initializeOwned(activityId,
                    activityPrize.getId(), remaining, attemptToken);
        }
    }

    @Override
    public void cleanupOwned(Long activityId, String attemptToken, boolean removeInventory) {
        prizeInventoryInitializer.cleanupOwned(activityId, attemptToken, removeInventory);
    }

    @Override
    public List<Long> prepare(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("Invalid activityId");
        }

        List<ActivityPrize> activityPrizes = 
        activityPrizeService.findEnabledByActivityId(activityId);
        if (activityPrizes == null || activityPrizes.isEmpty()) {
            throw new IllegalStateException("Activity prize pool is empty");
        }

        // 只记录本次实际写入成功的 key，失败补偿不能删除流程开始前已经存在的库存状态。
        List<Long> initializedActivityPrizeIds = new ArrayList<>(activityPrizes.size());
        try {
            for (ActivityPrize activityPrize : activityPrizes) {
                Long activityPrizeId = activityPrize.getId();
                Integer remainingInventory = activityPrize.getRemainingInventory();
                if (remainingInventory == null) {
                    throw new IllegalStateException(
                        "Activity prize remaining inventory must not be null");
                }

                prizeInventoryInitializer.initialize(activityPrizeId, remainingInventory);
                initializedActivityPrizeIds.add(activityPrizeId);
            }
            return initializedActivityPrizeIds;
        } catch (RuntimeException originalFailure) {
            // 不能保留部分奖池，否则重试会被遗留 key 的 SET NX 拒绝；当前补偿为 best-effort。
            compensateInitializedInventories(activityId, 
                initializedActivityPrizeIds, originalFailure);
            throw originalFailure;
        }
    }

    @Override 
    public void compensateInitializedInventories(
            Long activityId,
            List<Long> initializedActivityPrizeIds,
            RuntimeException initializationFailure
    ) {
        for (Long activityPrizeId : initializedActivityPrizeIds) {
            try {
                prizeInventoryInitializer.removeInitialization(activityPrizeId);
            } catch (RuntimeException compensationFailure) {
                // suppressed exception 保留补偿故障，同时确保最初的初始化失败仍是主异常。
                initializationFailure.addSuppressed(compensationFailure);
                LOGGER.error(
                        "Failed to compensate Redis prize inventory initialization, activityId={}, activityPrizeId={}",
                        activityId,
                        activityPrizeId,
                        compensationFailure
                );
            }
        }
    }

}
