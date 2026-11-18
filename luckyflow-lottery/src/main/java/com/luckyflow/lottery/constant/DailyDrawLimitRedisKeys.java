package com.luckyflow.lottery.constant;

import java.time.LocalDate;

/**
 * 每日抽奖次数领域使用的 Redis key。
 *
 * <p>key 按活动、用户和业务日期隔离；日期由调用方使用业务时钟计算后传入。</p>
 */
public final class DailyDrawLimitRedisKeys {

    private static final String DAILY_COUNT_KEY_PREFIX = "luckyflow:draw:daily-count:";

    private DailyDrawLimitRedisKeys() {
    }

    /** 构造指定活动、用户和业务日期的每日抽奖计数 key。 */
    public static String dailyCountKey(long activityId, long userId, LocalDate businessDate) {
        return DAILY_COUNT_KEY_PREFIX + activityId + ":" + userId + ":" + businessDate;
    }

    /** 判断是否为每日抽奖计数资源 key，防止预构造凭据绑定到错误资源。 */
    public static boolean isDailyCountKey(String redisKey) {
        return redisKey != null
                && redisKey.length() > DAILY_COUNT_KEY_PREFIX.length()
                && redisKey.startsWith(DAILY_COUNT_KEY_PREFIX);
    }
}
