package com.luckyflow.service;

import com.luckyflow.lottery.service.Interface.PrizeInventoryPreparationService;
import com.luckyflow.marketing.constant.ActivityStatusConstants;
import com.luckyflow.marketing.service.ActivityPrizeService;
import com.luckyflow.marketing.service.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单次活动发布的 MySQL 事务；Redis 本次写入可由外层已持久化的 intent 对账。 */
@Service
public class ActivityPublicationTransactionService {

    private final ActivityService activityService;
    private final ActivityPrizeService activityPrizeService;
    private final ActivityPublicationIntentService intentService;
    private final PrizeInventoryPreparationService inventory;

    public ActivityPublicationTransactionService(ActivityService activityService,
            ActivityPrizeService activityPrizeService,
            ActivityPublicationIntentService intentService,
            PrizeInventoryPreparationService inventory) {
        this.activityService = activityService;
        this.activityPrizeService = activityPrizeService;
        this.intentService = intentService;
        this.inventory = inventory;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long activityId, String token) {
        // 锁顺序固定为 Activity、intent、ActivityPrize、Prize；恢复也先锁 Activity 再锁 intent。
        activityService.lockActivityForPublication(activityId);
        intentService.requireActive(activityId, token);
        activityPrizeService.freezePrizeSnapshotsForPublication(activityId);
        inventory.prepareOwned(activityId, token);
        activityService.updateActivityStatus(activityId,
                ActivityStatusConstants.ACTIVITY_STATUS_PENDING_REVIEW,
                ActivityStatusConstants.ACTIVITY_STATUS_PUBLISHED);
    }
}
