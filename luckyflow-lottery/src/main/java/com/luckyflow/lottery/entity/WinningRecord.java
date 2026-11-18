package com.luckyflow.lottery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 关联中奖抽奖记录与活动奖品配置的持久化模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("winning_record")
public class WinningRecord {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long drawRecordId;

    private Long activityPrizeId;

    private LocalDateTime winTime;

    private LocalDateTime createTime;
}
