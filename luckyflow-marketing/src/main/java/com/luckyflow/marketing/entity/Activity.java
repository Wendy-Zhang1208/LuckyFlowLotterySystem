package com.luckyflow.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 营销活动及其抽奖参与规则的持久化模型。
 *
 * <p>数据库下划线命名字段通过 MyBatis-Plus 驼峰映射转换为 Java 属性。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity")
public class Activity {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 面向普通用户展示的活动摘要。 */
    private String summary;

    /** 前端 13 款固定主题之一；发布后与其他活动配置一起冻结。 */
    private String themeKey;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer dailyDrawLimit;

    private Integer pointCost;

    private Integer noWinWeight;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    
}
