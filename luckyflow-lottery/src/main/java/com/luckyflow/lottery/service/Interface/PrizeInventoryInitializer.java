package com.luckyflow.lottery.service.Interface;

/**
 * 将已经确定的活动奖品库存安全地初始化到运行期存储。
 *
 * <p>库存值为 0 仍表示“已初始化但已耗尽”；库存 key 不存在则表示“尚未初始化或状态异常”，
 * 两种状态必须严格区分。</p>
 */
public interface PrizeInventoryInitializer {

    /**
     * 初始化指定活动奖品的库存，禁止覆盖已经存在的运行期库存。
     *
     * @param activityPrizeId 活动奖品 ID，必须为正数
     * @param inventory 初始库存，允许为 0，但不能为负数
     * @throws IllegalArgumentException 参数不合法时抛出
     * @throws IllegalStateException 库存已初始化或 Redis 未返回明确执行结果时抛出
     */
    void initialize(Long activityPrizeId, int inventory);

    /**
     * 删除指定活动奖品的初始化库存。
     *
     * <p>该操作仅用于活动开放前奖池准备失败后的补偿清理，不得替代运行期的库存释放。</p>
     *
     * @param activityPrizeId 活动奖品 ID，必须为正数
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    void removeInitialization(Long activityPrizeId);

    /** 与归属标记和资源登记在同一 Redis Lua 中原子初始化，供持久化发布意图使用。 */
    void initializeOwned(Long activityId, Long activityPrizeId, int inventory, String attemptToken);

    /** 只清理与 attemptToken 匹配的资源；正式发布后仅移除归属标记而保留库存。 */
    void cleanupOwned(Long activityId, String attemptToken, boolean removeInventory);
}
