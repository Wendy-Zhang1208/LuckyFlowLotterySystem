package com.luckyflow.lottery.model;

/**
 * 一次奖品库存预占使用的稳定 Redis 凭据。
 *
 * @param redisKey 奖品库存资源的完整 Redis key
 * @param reservationKey 全局 reservation key
 */
public record PrizeInventoryReservation(
    String redisKey,
    String reservationKey
) {

}
