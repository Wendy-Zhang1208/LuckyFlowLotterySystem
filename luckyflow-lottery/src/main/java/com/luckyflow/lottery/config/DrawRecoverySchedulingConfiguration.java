package com.luckyflow.lottery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启可配置的抽奖请求持久化恢复扫描。 */
@Configuration
@EnableScheduling
public class DrawRecoverySchedulingConfiguration {
}
