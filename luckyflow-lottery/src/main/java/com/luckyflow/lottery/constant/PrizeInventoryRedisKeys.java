package com.luckyflow.lottery.constant;

/**
 * 奖品库存领域使用的 Redis key。
 *
 * <p>库存初始化、预扣和释放必须使用完全相同的 key 格式，统一构造可避免各组件格式漂移。</p>
 */
public final class PrizeInventoryRedisKeys {

    private static final String INVENTORY_KEY_PREFIX = "luckyflow:draw:inventory:";
    private static final String INIT_OWNER_KEY_PREFIX = "luckyflow:draw:init-owner:";
    private static final String INIT_REGISTRY_KEY_PREFIX = "luckyflow:draw:init-registry:";

    private PrizeInventoryRedisKeys() {
    }

    /** 构造指定活动奖品的库存 key。 */
    public static String inventoryKey(long activityPrizeId) {
        return INVENTORY_KEY_PREFIX + activityPrizeId;
    }

    public static String initializationOwnerKey(long activityPrizeId) {
        return INIT_OWNER_KEY_PREFIX + activityPrizeId;
    }

    public static String initializationRegistryKey(long activityId, String attemptToken) {
        return INIT_REGISTRY_KEY_PREFIX + activityId + ":" + attemptToken;
    }

    public static String inventoryKeyPrefix() { return INVENTORY_KEY_PREFIX; }

    public static String initializationOwnerKeyPrefix() { return INIT_OWNER_KEY_PREFIX; }

    /** 判断是否为活动奖品库存资源 key，防止预构造凭据绑定到错误资源。 */
    public static boolean isInventoryKey(String redisKey) {
        return redisKey != null
                && redisKey.length() > INVENTORY_KEY_PREFIX.length()
                && redisKey.startsWith(INVENTORY_KEY_PREFIX);
    }
}
