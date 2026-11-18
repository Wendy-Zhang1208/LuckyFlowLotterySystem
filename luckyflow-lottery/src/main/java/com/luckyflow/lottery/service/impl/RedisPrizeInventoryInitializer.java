package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.constant.PrizeInventoryRedisKeys;
import com.luckyflow.lottery.service.Interface.PrizeInventoryInitializer;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的奖品库存初始化实现。
 *
 * <p>单条 命令能够原子完成“不存在才写入”，并发初始化时只允许一个调用成功，
 * 避免覆盖已经发生预扣或释放的运行期库存。本组件不使用 Redis 事务，也不为库存 key 设置 TTL。</p>
 */
@Service
public class RedisPrizeInventoryInitializer implements PrizeInventoryInitializer {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> ownedInitializationScript;
    private final DefaultRedisScript<Long> ownedCleanupScript;

    public RedisPrizeInventoryInitializer(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ownedInitializationScript = script("lua/inventory-init-owned.lua");
        this.ownedCleanupScript = script("lua/inventory-cleanup-owned.lua");
    }

    @Override
    public void initialize(Long activityPrizeId, int inventory) {
        if (activityPrizeId == null || activityPrizeId <= 0) {
            throw new IllegalArgumentException("Invalid activityPrizeId");
        }
        if (inventory < 0) {
            throw new IllegalArgumentException("Inventory must not be negative");
        }

        String key = PrizeInventoryRedisKeys.inventoryKey(activityPrizeId);
        // 使用 SET NX 保证库存仅能首次初始化，避免覆盖已经存在的运行期库存状态。
        Boolean initialized = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, Integer.toString(inventory));

        if (Boolean.TRUE.equals(initialized)) {
            return;
        }
        if (Boolean.FALSE.equals(initialized)) {
            throw new IllegalStateException("Prize inventory has already been initialized");
        }

        // Spring Data Redis 使用包装类型返回结果；null 表示结果不明确，不能当作初始化成功。
        throw new IllegalStateException("Redis inventory initialization returned null");
    }

    @Override
    public void removeInitialization(Long activityPrizeId) {
        if (activityPrizeId == null || activityPrizeId <= 0) {
            throw new IllegalArgumentException("Invalid activityPrizeId");
        }

        // 仅供活动开放前的失败补偿使用；运行期库存恢复必须继续走 reservation release。
        stringRedisTemplate.delete(PrizeInventoryRedisKeys.inventoryKey(activityPrizeId));
    }

    @Override
    public void initializeOwned(Long activityId, Long activityPrizeId, int inventory, String attemptToken) {
        validateOwnedArguments(activityId, attemptToken);
        if (activityPrizeId == null || activityPrizeId <= 0 || inventory < 0) {
            throw new IllegalArgumentException("Invalid activity prize inventory");
        }
        Long result = stringRedisTemplate.execute(ownedInitializationScript,
                List.of(
                        PrizeInventoryRedisKeys.inventoryKey(activityPrizeId),
                        PrizeInventoryRedisKeys.initializationOwnerKey(activityPrizeId),
                        PrizeInventoryRedisKeys.initializationRegistryKey(activityId, attemptToken)
                ), Integer.toString(inventory), attemptToken, activityPrizeId.toString());
        if (result == null || result != 1L) {
            throw new IllegalStateException("Owned prize inventory initialization failed, result=" + result);
        }
    }

    @Override
    public void cleanupOwned(Long activityId, String attemptToken, boolean removeInventory) {
        validateOwnedArguments(activityId, attemptToken);
        Long result = stringRedisTemplate.execute(ownedCleanupScript,
                List.of(PrizeInventoryRedisKeys.initializationRegistryKey(activityId, attemptToken)),
                attemptToken,
                PrizeInventoryRedisKeys.initializationOwnerKeyPrefix(),
                PrizeInventoryRedisKeys.inventoryKeyPrefix(),
                removeInventory ? "1" : "0");
        if (result == null) {
            throw new IllegalStateException("Owned prize inventory cleanup result is unknown");
        }
    }

    private void validateOwnedArguments(Long activityId, String attemptToken) {
        if (activityId == null || activityId <= 0 || attemptToken == null) {
            throw new IllegalArgumentException("Invalid publication attempt");
        }
        try { UUID.fromString(attemptToken); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid publication attempt token", invalid);
        }
    }

    private DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
