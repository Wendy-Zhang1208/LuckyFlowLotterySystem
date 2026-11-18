package com.luckyflow.lottery.service.Interface;

import java.util.Optional;

import com.luckyflow.lottery.model.DailyDrawReservation;

/**
 * 协调用户每日抽奖额度的预占与补偿。
 */
public interface DailyDrawLimitService {

    /**
     * 读取用户在当前业务日已经占用的抽奖次数。
     *
     * <p>该值同时包含已提交消费和仍在处理的 Redis 预占，适合参与资格查询使用；
     * 它只是只读快照，真正抽奖仍必须调用 {@link #tryReserve(DailyDrawReservation, Integer)}
     * 进行原子校验。</p>
     *
     * @param userId 当前可信用户标识
     * @param activityId 活动标识
     * @return 当前业务日已经占用的次数，Redis key 不存在时返回 {@code 0}
     */
    int getUsedCount(Long userId, Long activityId);

    /**
     * 尝试预占一次每日抽奖额度。
     *
     * @param reservation 由持久化 intent 预先生成的稳定 Redis 凭据
     * @param dailyLimit 当前业务日期允许的最大抽奖次数
     * @return 成功时返回预占凭据，达到上限时返回空结果
     */
    Optional<DailyDrawReservation> tryReserve(
            DailyDrawReservation reservation,
            Integer dailyLimit
    );

    /** 将成功事务使用的次数预占凭证推进为不可释放的确认终态。 */
    void confirm(DailyDrawReservation reservation);

    /**
     * 补偿一次此前成功的额度预占。
     *
     * @param reservation 与预占时相同的稳定凭据
     */
    void release(DailyDrawReservation reservation);
}
