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
 * 可复用奖品目录定义的持久化模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("prize")
public class Prize {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 面向用户的奖品说明，不包含内部库存或权重信息。 */
    private String description;

    /** 前端可识别的插画资源键或受控地址。 */
    private String illustration;

    private Integer type;

    private Integer totalInventory;

    private Integer availableInventory;

    private BigDecimal value;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
