package com.luckyflow.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供时间敏感型领域服务使用的业务时钟。
 */
@Configuration 
public class TimeConfig {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 创建用于计算每日抽奖次数日期和过期时间的生产环境时钟。
     *
     * @return 绑定应用业务时区的系统时钟
     */
    @Bean
    public Clock dailyDrawLimitClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
