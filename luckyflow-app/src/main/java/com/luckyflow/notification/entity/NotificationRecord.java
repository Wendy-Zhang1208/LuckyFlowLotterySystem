package com.luckyflow.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一条可恢复的通知发送意图；recipient 保存创建意图时的联系方式快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("notification_record")
public class NotificationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long winningRecordId;

    private Long userId;

    private Integer channel;

    private String recipient;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String processingToken;

    private LocalDateTime processingTime;

    private LocalDateTime sendTime;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
