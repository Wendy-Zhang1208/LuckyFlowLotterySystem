package com.luckyflow.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户当前积分余额的持久化模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("point_account")
public class PointAccount {

    /** 数据库生成的主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long balance;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
