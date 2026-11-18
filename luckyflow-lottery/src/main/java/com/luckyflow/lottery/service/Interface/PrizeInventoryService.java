package com.luckyflow.lottery.service.Interface;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.luckyflow.lottery.model.PrizeInventoryReservation;

public interface PrizeInventoryService {

    /**
     * 读取实时redisKey快照
     * @param activityPrizeIds
     * @return
     */
    Map<Long, Integer> getInventorySnapshots(List<Long> activityPrizeIds);

    /**
     * 使用预构造的稳定凭据尝试预占一份库存；重复调用不会再次扣减。
     */
    Optional<PrizeInventoryReservation> tryReserve(PrizeInventoryReservation reservation);

    /** 将成功事务使用的预占凭证推进为不可释放的确认终态。 */
    void confirm(PrizeInventoryReservation reservation);

    void release(PrizeInventoryReservation reservation);

}
