package com.luckyflow.lottery.model;

/**
 * 每日抽奖额度预占成功后的凭据。
 *
 * <p>两个 key 均由持久化 reservation intent 预先确定。重复 reserve 和 release 必须复用
 * 同一凭据，才能由 Redis 协议识别同一次业务尝试。</p>
 *
 * @param redisKey 每日次数资源的完整 Redis key
 * @param reservationKey 全局 reservation key
 */
public record DailyDrawReservation(
    String redisKey,
    String reservationKey
) {
}
