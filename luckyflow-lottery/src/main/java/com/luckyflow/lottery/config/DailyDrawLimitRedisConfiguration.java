package com.luckyflow.lottery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 注册用于预占和释放每日抽奖额度的 Redis Lua 脚本。
 */
@Configuration
public class DailyDrawLimitRedisConfiguration {

    /**
     * 从应用类路径加载每日额度原子预占脚本。
     *
     * @return 成功或同一 RESERVED 重试返回 {@code 1}，业务拒绝或终态重试返回 {@code 0} 的脚本
     */
    @Bean
    public DefaultRedisScript<Long> dailyDrawLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/daily-draw-limit.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 从应用类路径加载额度补偿释放脚本。
     *
     * @return 释放成功时返回 {@code 1}、终态或预先取消时返回 {@code 0} 的脚本
     */
    @Bean 
    public DefaultRedisScript<Long> dailyTimeReleaseScript(){
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/release-draw-time.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** 加载成功请求将每日次数 reservation 推进为 CONFIRMED 的原子脚本。 */
    @Bean
    public DefaultRedisScript<Long> dailyReservationConfirmScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/confirm-draw-reservation.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
