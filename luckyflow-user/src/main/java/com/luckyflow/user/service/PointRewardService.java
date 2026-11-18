package com.luckyflow.user.service;

import java.time.LocalDateTime;

/** 对外提供中奖积分奖励的并发安全、业务幂等发放能力。 */
public interface PointRewardService {

    /**
     * 为一条中奖记录发放积分；相同中奖事实重复调用时正常返回。
     */
    void grantLotteryReward(
            Long winningRecordId,
            Long userId,
            long points,
            LocalDateTime now
    );
}
