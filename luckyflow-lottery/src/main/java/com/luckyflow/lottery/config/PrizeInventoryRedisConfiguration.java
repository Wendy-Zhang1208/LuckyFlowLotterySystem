package com.luckyflow.lottery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 注册库存资源与全局 reservation 状态原子变更所需的 Lua 脚本。 */
@Configuration 
public class PrizeInventoryRedisConfiguration {

    /** 加载库存扣减与 reservation 状态写入的原子脚本。 */
    @Bean 
    public DefaultRedisScript<Long> inventoryReserveScript(){
        DefaultRedisScript<Long> script= new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/inventory-reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** 加载库存恢复与 reservation 终态写入的原子脚本。 */
    @Bean 
    public DefaultRedisScript<Long> inventoryReleaseScript(){
        DefaultRedisScript<Long> script= new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/inventory-release.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /** 加载成功请求将库存 reservation 推进为 CONFIRMED 的原子脚本。 */
    @Bean
    public DefaultRedisScript<Long> inventoryReservationConfirmScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/confirm-draw-reservation.lua"));
        script.setResultType(Long.class);
        return script;
    }

}
