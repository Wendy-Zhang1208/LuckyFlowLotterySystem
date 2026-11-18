package com.luckyflow.lottery.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor   
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;

    private Long aggregateId;

    private String payload;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private LocalDateTime processingTime;

    private String processingToken;

    private LocalDateTime publishTime;

    private String lastError;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
