package com.luckyflow.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 活动详情页展示的有序规则条目。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity_rule")
public class ActivityRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String title;

    private String content;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
