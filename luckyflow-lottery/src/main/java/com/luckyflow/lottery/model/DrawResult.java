package com.luckyflow.lottery.model;

/**
 * 抽奖事务完成后返回的不可变服务结果。
 *
 * @param drawRecordId 已持久化的抽奖记录标识
 * @param won 是否中奖
 * @param prizeId 中奖奖品标识，未中奖时为 {@code null}
 * @param prizeName 中奖奖品名称，未中奖时为 {@code null}
 * @param prizeType 中奖奖品类型，未中奖时为 {@code null}
 */
public record DrawResult(
        Long drawRecordId,
        boolean won,
        Long prizeId,
        String prizeName,
        Integer prizeType
) {
}
