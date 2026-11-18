package com.luckyflow.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抽奖请求的一次 Redis 资源预占意图及其最终处理结果。
 *
 * <p>该记录在执行 Redis 命令前创建，使恢复流程能够定位可能需要确认或补偿的预占。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("draw_resource_reservation")
public class DrawResourceReservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Integer resourceType;

    private Long resourceId;

    /** 同一种资源允许存在多次竞争尝试，attemptNo 用于区分每次独立意图。 */
    private Integer attemptNo;

    private String resourceKey;

    /** 一次预占的稳定业务标识，在 PREPARED 阶段即确定。 */
    private String reservationId;

    /** Redis 中用于识别并幂等补偿本次预占的 key。 */
    private String reservationKey;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lastError;

    private LocalDateTime reservedTime;

    private LocalDateTime resolvedTime;

    /** Redis marker 已安全推进为成功终态的时间；为空时由恢复任务继续确认。 */
    private LocalDateTime terminalizedTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
