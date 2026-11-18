package com.luckyflow.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一次具有全局幂等标识的抽奖请求。
 *
 * <p>executionToken 用于隔离不同执行轮次，恢复任务不得用旧 token 推进新一轮执行。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("draw_request")
public class DrawRequest {

    /** 调用方提供的全局幂等请求标识。 */
    @TableId(value = "request_id", type = IdType.INPUT)
    private String requestId;

    private Long userId;

    private Long activityId;

    private Integer status;

    private String executionToken;

    private LocalDateTime executionTime;

    /** 成功结果的 JSON 快照，用于幂等重放。 */
    private String resultPayload;

    private LocalDateTime completedTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
