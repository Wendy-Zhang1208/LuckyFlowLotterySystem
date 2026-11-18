package com.luckyflow.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 每次已完成抽奖尝试的持久化模型，包含中奖和未中奖结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("draw_record")
public class DrawRecord {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private Integer resultStatus;

    private LocalDateTime drawTime;

    private LocalDateTime createTime;
}
