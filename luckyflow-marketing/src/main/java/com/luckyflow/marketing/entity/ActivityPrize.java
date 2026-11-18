package com.luckyflow.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 关联活动、奖品及其权重和库存分配的持久化模型。
 *
 * <p>外键仅表示为标识字段，使 MyBatis 模型不包含 ORM 关系语义。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_prize")
public class ActivityPrize {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long prizeId;

    /**
     * 活动发布时冻结的奖品类型。发布后的抽奖与履约不得再读取共享奖品目录的可变类型。
     */
    private Integer prizeTypeSnapshot;

    /**
     * 活动发布时冻结的奖励值；积分奖品履约以该快照为准，避免目录调整改写历史承诺。
     */
    private BigDecimal prizeValueSnapshot;

    private Integer weight;

    private Integer allocatedInventory;

    private Integer remainingInventory;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
