package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.constant.DailyDrawLimitRedisKeys;
import com.luckyflow.lottery.constant.DrawReservationRedisKeys;
import com.luckyflow.lottery.model.DailyDrawReservation;
import com.luckyflow.lottery.service.Interface.DailyDrawLimitService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 基于 Redis 实现的每日抽奖额度预占服务。
 *
 * <p>Lua 脚本将计数变更与全局 reservation 状态写入作为一个原子操作。调用方必须传入
 * 已持久化的稳定 key；每日计数和对应 marker 均在本地时区下一自然日开始时过期。</p>
 */
@Service
public class RedisDailyDrawLimitService implements DailyDrawLimitService {

    private static final long LUA_SUCCESS = 1L;
    private static final long LUA_LIMIT_REACHED = 0L;
    private static final long LUA_NO_OP = 0L;
    private static final long LUA_RESERVATION_CONFLICT = -2L;
    private static final long LUA_TERMINAL_STATE_CONFLICT = -4L;

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> dailyDrawLimitScript;
    private final DefaultRedisScript<Long> dailyTimeReleaseScript;
    private final DefaultRedisScript<Long> dailyReservationConfirmScript;
    private final Clock clock;
    private final long missingReservationTtlSeconds;

    /**
     * 使用 Redis 访问组件、预占/释放脚本和业务时钟创建服务。
     */
    public RedisDailyDrawLimitService(
            StringRedisTemplate stringRedisTemplate,
            @Qualifier ("dailyDrawLimitScript")
            DefaultRedisScript<Long> dailyDrawLimitScript,
            @Qualifier ("dailyTimeReleaseScript")
            DefaultRedisScript<Long> dailyTimeReleaseScript,
            @Qualifier ("dailyReservationConfirmScript")
            DefaultRedisScript<Long> dailyReservationConfirmScript,
            Clock clock,
            @Value("${luckyflow.lottery.redis.daily-missing-reservation-ttl-seconds:86400}")
            long missingReservationTtlSeconds
    ) {
        if (missingReservationTtlSeconds <= 0) {
            throw new IllegalArgumentException("Daily reservation cancellation TTL must be positive");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.dailyDrawLimitScript = dailyDrawLimitScript;
        this.dailyTimeReleaseScript = dailyTimeReleaseScript;
        this.dailyReservationConfirmScript = dailyReservationConfirmScript;
        this.clock = clock;
        this.missingReservationTtlSeconds = missingReservationTtlSeconds;
    }

    @Override
    public int getUsedCount(Long userId, Long activityId) {
        if (userId == null || userId <= 0 || activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("userId and activityId must be positive");
        }
        String redisKey = DailyDrawLimitRedisKeys.dailyCountKey(
                activityId,
                userId,
                LocalDate.now(clock)
        );
        String value = stringRedisTemplate.opsForValue().get(redisKey);
        if (value == null) {
            return 0;
        }
        try {
            int usedCount = Integer.parseInt(value);
            if (usedCount < 0) {
                throw new IllegalStateException("Daily draw count must not be negative");
            }
            return usedCount;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid daily draw count", exception);
        }
    }

    @Override
    public Optional<DailyDrawReservation> tryReserve(
            DailyDrawReservation reservation,
            Integer dailyLimit
    ) {
        validateReservation(reservation);
        if (dailyLimit == null || dailyLimit <= 0) {
            throw new IllegalArgumentException("Invalid dailyLimit");
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        long ttlSeconds = calculateTtlSeconds(now);

        // 资源变更和 reservation 状态写入必须由同一 Lua 原子完成，避免重试重复计数。
        Long result = stringRedisTemplate.execute(
                dailyDrawLimitScript,
                List.of(reservation.redisKey(), reservation.reservationKey()),
                String.valueOf(dailyLimit),
                String.valueOf(ttlSeconds),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_RESERVED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_REJECTED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_RELEASED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_CANCELLED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_CONFIRMED)
        );

        if (result == null) {
            throw new IllegalStateException("Redis daily draw limit script returned null");
        }
        if (result == LUA_SUCCESS) {
            return Optional.of(reservation);
        }
        if (result == LUA_LIMIT_REACHED) {
            return Optional.empty();
        }
        if (result == -1L) {
            throw new IllegalStateException(
                    "Daily draw count key is missing for an existing reservation"
            );
        }
        if (result == LUA_RESERVATION_CONFLICT) {
            throw new IllegalStateException(
                    "Daily draw reservation key is bound to another resource"
            );
        }
        throw new IllegalStateException("Unexpected Redis daily draw limit script result: " + result);
    }

    @Override
    public void confirm(DailyDrawReservation reservation) {
        validateReservation(reservation);
        Long result = stringRedisTemplate.execute(
                dailyReservationConfirmScript,
                List.of(reservation.reservationKey()),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_RESERVED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_CONFIRMED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_REJECTED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_RELEASED),
                reservationValue(reservation, DrawReservationRedisKeys.STATUS_CANCELLED)
        );
        if (result == null) {
            throw new IllegalStateException("Redis daily draw reservation confirm returned null");
        }
        if (result == LUA_SUCCESS || result == LUA_NO_OP) {
            return;
        }
        if (result == LUA_RESERVATION_CONFLICT) {
            throw new IllegalStateException(
                    "Daily draw reservation key is bound to another resource"
            );
        }
        if (result == LUA_TERMINAL_STATE_CONFLICT) {
            throw new IllegalStateException(
                    "Daily draw reservation has already reached an incompatible terminal state"
            );
        }
        throw new IllegalStateException(
                "Unexpected Redis daily draw reservation confirm result: " + result
        );
    }

    private long calculateTtlSeconds(ZonedDateTime now) {
        LocalDate currentDate = now.toLocalDate();

        ZonedDateTime nextDayStart = currentDate.plusDays(1).atStartOfDay(now.getZone());

        Duration duration = Duration.between(now, nextDayStart);

        if(duration.isNegative() || duration.isZero()){
            throw new IllegalStateException("Invalid daily draw TTL duration: " + duration);
        }

        long ttlSeconds = duration.getSeconds();
        // Redis 过期时间使用整数秒，向上取整可避免键提前过期。
        if (duration.getNano() > 0) {
            ttlSeconds++;
        }

        return ttlSeconds;
    }

    @Override
    public void release(DailyDrawReservation reservation) {
        validateReservation(reservation);

        // marker 缺失时 Lua 写 CANCELLED 墓碑，阻止稍后到达的 reserve 再修改每日次数。
        Long result = stringRedisTemplate.execute(
            dailyTimeReleaseScript,
            List.of(reservation.redisKey(),
                    reservation.reservationKey()),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_RESERVED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_REJECTED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_RELEASED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_CANCELLED),
            reservationValue(reservation, DrawReservationRedisKeys.STATUS_CONFIRMED),
            Long.toString(missingReservationTtlSeconds)
        );

        if (result == null) {
            throw new IllegalStateException(
                    "Redis daily draw release script returned null"
            );
        }
        
        if (result != LUA_NO_OP && result != LUA_SUCCESS) {
            if (result == -1L) {
                throw new IllegalStateException(
                        "Daily draw count key does not exist during release"
                );
            }
            if (result == LUA_RESERVATION_CONFLICT) {
                throw new IllegalStateException(
                        "Daily draw reservation key is bound to another resource"
                );
            }
            throw new IllegalStateException(
                    "Unexpected Redis daily draw release script result: " + result
            );
        }
    }

    private void validateReservation(DailyDrawReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation must not be null");
        }
        if (!DailyDrawLimitRedisKeys.isDailyCountKey(reservation.redisKey())) {
            throw new IllegalArgumentException("Invalid redisKey");
        }
        if (!DrawReservationRedisKeys.isReservationKey(reservation.reservationKey())) {
            throw new IllegalArgumentException("Invalid reservationKey");
        }
    }

    private String reservationValue(DailyDrawReservation reservation, String status) {
        return DrawReservationRedisKeys.reservationValue(
                DrawReservationRedisKeys.RESOURCE_TYPE_DAILY_DRAW_LIMIT,
                reservation.redisKey(),
                status
        );
    }
}
