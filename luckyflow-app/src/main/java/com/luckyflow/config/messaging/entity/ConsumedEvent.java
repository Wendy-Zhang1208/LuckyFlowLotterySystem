package com.luckyflow.config.messaging.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息消费幂等记录，对应 {@code consumed_event} 表。
 *
 * <p>同一消费者与事件的组合由数据库唯一键约束，作为并发消费时的最终去重屏障。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("consumed_event")
public class ConsumedEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private String consumerName;

    private LocalDateTime consumeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
