package com.luckyflow.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物理奖品履约订单的持久化模型。
 *
 * <p>收货信息在中奖时尚未填写，因此相关字段允许为空；状态和各生命周期时间与
 * {@code fulfillment_order} 表保持一一对应。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("fulfillment_order")
public class FulfillmentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long winningRecordId;

    private Long userId;

    private String consigneeName;

    private String consigneePhone;

    private String province;

    private String city;

    private String district;

    private String detailAddress;

    private String logisticsCompany;

    private String trackingNumber;

    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime deliveryTime;

    private LocalDateTime receiveTime;

    private LocalDateTime completeTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
