package com.luckyflow.marketing.service;

import com.luckyflow.marketing.entity.ActivityPrize;

import java.util.List;

/**
 * 对外提供活动奖品配置查询和库存扣减能力。
 */
public interface ActivityPrizeService {

    List<ActivityPrize> findByActivityId(Long activityId);

    ActivityPrize findActivityPrize(Long activityPrizeId);

    /**
     * 筛选对应活动数据库可用奖品
     * @param activityId
     * @return
     */
    List<ActivityPrize> findEnabledByActivityId(Long activityId);

    /** 在可编辑活动中增加一项奖池配置。 */
    ActivityPrize createActivityPrize(ActivityPrize activityPrize);

    /**
     * 更新奖池业务配置。{@code activityId} 作为锁定顺序和归属校验的一部分，不能只依赖记录 ID。
     */
    void updateActivityPrizeConfiguration(Long activityId, ActivityPrize activityPrize);

    /** 在可编辑活动中删除一项奖池配置。 */
    void deleteActivityPrize(Long activityId, Long activityPrizeId);

    /**
     * 锁定待发布活动及奖池，将共享奖品的类型和奖励值冻结到活动奖品快照中。
     *
     * @return 已启用且已经完成快照冻结的活动奖品，按主键升序返回
     */
    List<ActivityPrize> freezePrizeSnapshotsForPublication(Long activityId);

    /**
     * 仅在剩余库存大于零时原子扣减一份库存。
     */
    boolean tryDeductInventory(Long activityPrizeId);
}
