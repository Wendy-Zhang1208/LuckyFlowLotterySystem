package com.luckyflow.lottery.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.luckyflow.lottery.constant.PrizeInventoryRedisKeys;
import com.luckyflow.lottery.constant.DrawReservationRedisKeys;
import com.luckyflow.lottery.model.PrizeInventoryReservation;
import com.luckyflow.lottery.service.Interface.PrizeInventoryService;

/**
 * 通过 Redis Lua 原子维护库存与全局 reservation 状态。
 *
 * <p>已扣减库存的 RESERVED marker 不设 TTL，避免响应未知时丢失是否扣减的证据；
 * 只有明确的终态 marker 才使用可配置的保留时间。</p>
 */
@Service 
public class RedisPrizeInventoryService implements PrizeInventoryService{

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> inventoryReserveScript;
    private final DefaultRedisScript<Long> inventoryReleaseScript;
    private final DefaultRedisScript<Long> inventoryReservationConfirmScript;

    private static final long LUA_RESERVATION_CONFLICT = -2L;
    private static final long LUA_TERMINAL_STATE_CONFLICT = -4L;
    private final long terminalReservationTtlSeconds;

    public RedisPrizeInventoryService(
        StringRedisTemplate stringRedisTemplate,
        @Qualifier ("inventoryReserveScript")
        DefaultRedisScript<Long> inventoryReserveScript,
        @Qualifier ("inventoryReleaseScript")
        DefaultRedisScript<Long> inventoryReleaseScript,
        @Qualifier ("inventoryReservationConfirmScript")
        DefaultRedisScript<Long> inventoryReservationConfirmScript,
        @Value("${luckyflow.lottery.redis.inventory-terminal-reservation-ttl-seconds:604800}")
        long terminalReservationTtlSeconds
    ){
        if (terminalReservationTtlSeconds <= 0) {
            throw new IllegalArgumentException("Inventory terminal reservation TTL must be positive");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.inventoryReserveScript = inventoryReserveScript;
        this.inventoryReleaseScript = inventoryReleaseScript;
        this.inventoryReservationConfirmScript = inventoryReservationConfirmScript;
        this.terminalReservationTtlSeconds = terminalReservationTtlSeconds;
    }

    @Override
    public Optional<PrizeInventoryReservation> tryReserve(
            PrizeInventoryReservation reservation
    ) {
        validateReservation(reservation);

        Long result = stringRedisTemplate.execute(
            inventoryReserveScript,
            List.of(reservation.redisKey(), reservation.reservationKey()),
            // 该 TTL 只用于终态；Lua 会让已实际扣减库存的 RESERVED marker 永不过期。
            Long.toString(terminalReservationTtlSeconds),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_RESERVED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_REJECTED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_RELEASED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_CANCELLED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_CONFIRMED)
        );

        if(result == null){
            throw new IllegalStateException("Redis inventory reserve returned null");
        }

        if(result == 1L){
            return Optional.of(reservation);
        }

        if(result == 0L){
            return Optional.empty();
        }

        if (result == -1L) {
            throw new IllegalStateException("Prize inventory is not initialized");
        }

        if (result == LUA_RESERVATION_CONFLICT) {
            throw new IllegalStateException(
                    "Prize inventory reservation key is bound to another resource"
            );
        }

        throw new IllegalStateException("Unexpected inventory reserve result: " + result);
    }

    @Override
    public void confirm(PrizeInventoryReservation reservation) {
        validateReservation(reservation);
        Long result = stringRedisTemplate.execute(
                inventoryReservationConfirmScript,
                List.of(reservation.reservationKey()),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_RESERVED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_CONFIRMED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_REJECTED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_RELEASED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_CANCELLED)
        );
        validateConfirmationResult(result, "Prize inventory");
    }

    @Override
    public void release(PrizeInventoryReservation prizeInventoryReservation) {
        validateReservation(prizeInventoryReservation);

        Long result = stringRedisTemplate.execute(
            inventoryReleaseScript,
            List.of(prizeInventoryReservation.redisKey(),
                    prizeInventoryReservation.reservationKey()),
            Long.toString(terminalReservationTtlSeconds),
            reservationValue(prizeInventoryReservation, DrawReservationRedisKeys.STATUS_RESERVED),
            reservationValue(prizeInventoryReservation, DrawReservationRedisKeys.STATUS_REJECTED),
            reservationValue(prizeInventoryReservation, DrawReservationRedisKeys.STATUS_RELEASED),
            reservationValue(prizeInventoryReservation, DrawReservationRedisKeys.STATUS_CANCELLED),
            reservationValue(prizeInventoryReservation, DrawReservationRedisKeys.STATUS_CONFIRMED)
        );

        if (result == null) {
            throw new IllegalStateException(
                "Redis inventory release returned null"
            );
        }

        if (result == 1L || result == 0L) {
            return;
        }
    
        if (result == -1L) {
            throw new IllegalStateException(
                    "Prize inventory key does not exist during release"
            );
        }

        if (result == LUA_RESERVATION_CONFLICT) {
            throw new IllegalStateException(
                    "Prize inventory reservation key is bound to another resource"
            );
        }
    
        throw new IllegalStateException(
                "Unexpected inventory release result: " + result
        );
    }

    @Override
    public Map<Long, Integer> getInventorySnapshots(List<Long> activityPrizeIds) {
        if (activityPrizeIds == null) {
            throw new IllegalArgumentException("activityPrizeIds must not be null");
        }
    
        if (activityPrizeIds.isEmpty()) {
            return Map.of();
        }
    
        List<String> keys = activityPrizeIds.stream()
            .map(activityPrizeId -> {
                if (activityPrizeId == null || activityPrizeId <= 0) {
                    throw new IllegalArgumentException("Invalid activityPrizeId");
                }
                return PrizeInventoryRedisKeys.inventoryKey(activityPrizeId);
            })
            .toList();
        
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

        if (values == null || values.size() != activityPrizeIds.size()) {
            throw new IllegalStateException(
                "Redis inventory snapshot result is invalid"
            );
        }

        Map<Long, Integer> snapshots = new LinkedHashMap<>();

        for (int i = 0; i < activityPrizeIds.size(); i++) {
            Long activityPrizeId = activityPrizeIds.get(i);
            String value = values.get(i);
    
            if (value == null) {
                throw new IllegalStateException(
                    "Prize inventory is not initialized: " + activityPrizeId
                );
            }
    
            int inventory;
            try {
                inventory = Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                    "Invalid prize inventory value: " + activityPrizeId,
                    exception
                );
            }
    
            if (inventory < 0) {
                throw new IllegalStateException(
                    "Prize inventory must not be negative: " + activityPrizeId
                );
            }
    
            snapshots.put(activityPrizeId, inventory);
        }
    
        return snapshots;
        
    }

    private void validateReservation(PrizeInventoryReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation must not be null");
        }
        if (!PrizeInventoryRedisKeys.isInventoryKey(reservation.redisKey())) {
            throw new IllegalArgumentException("Invalid redisKey");
        }
        if (!DrawReservationRedisKeys.isReservationKey(reservation.reservationKey())) {
            throw new IllegalArgumentException("Invalid reservationKey");
        }
    }

    private String reservationValue(PrizeInventoryReservation reservation, String status) {
        return DrawReservationRedisKeys.reservationValue(
                DrawReservationRedisKeys.RESOURCE_TYPE_PRIZE_INVENTORY,
                reservation.redisKey(),
                status
        );
    }

    private void validateConfirmationResult(Long result, String resourceName) {
        if (result == null) {
            throw new IllegalStateException(resourceName + " reservation confirm returned null");
        }
        if (result == 1L || result == 0L) {
            return;
        }
        if (result == LUA_RESERVATION_CONFLICT) {
            throw new IllegalStateException(
                    resourceName + " reservation key is bound to another resource"
            );
        }
        if (result == LUA_TERMINAL_STATE_CONFLICT) {
            throw new IllegalStateException(
                    resourceName + " reservation has already reached an incompatible terminal state"
            );
        }
        throw new IllegalStateException(
                "Unexpected " + resourceName.toLowerCase() + " reservation confirm result: " + result
        );
    }

}
