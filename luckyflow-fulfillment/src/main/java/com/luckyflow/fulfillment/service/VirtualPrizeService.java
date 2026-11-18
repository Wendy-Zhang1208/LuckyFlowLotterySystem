package com.luckyflow.fulfillment.service;

import com.luckyflow.fulfillment.entity.UserVirtualPrize;

import java.time.LocalDateTime;

/** 提供虚拟奖品发放和查询能力。 */
public interface VirtualPrizeService {

    void initializeVirtualPrize(
            Long winningRecordId,
            Long userId,
            Long prizeId,
            LocalDateTime now
    );

    UserVirtualPrize findByWinningRecordId(Long winningRecordId);
}
