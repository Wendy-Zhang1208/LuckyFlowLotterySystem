package com.luckyflow.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户实际获得的一份虚拟奖品。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_virtual_prize")
public class UserVirtualPrize {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long prizeId;

    private Long winningRecordId;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime useTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
