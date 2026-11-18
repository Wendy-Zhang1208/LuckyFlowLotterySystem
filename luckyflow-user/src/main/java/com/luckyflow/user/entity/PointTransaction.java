package com.luckyflow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户积分账本中不可变余额变更记录的持久化模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("point_transaction")
public class PointTransaction {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String bizType;

    private Long bizId;

    private Long changeAmount;

    private Long afterBalance;

    private LocalDateTime createTime;
}
