package com.luckyflow.fulfillment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.fulfillment.entity.FulfillmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 履约订单的数据访问接口，仅供 Fulfillment 模块内部使用。
 */
@Mapper
public interface FulfillmentOrderMapper extends BaseMapper<FulfillmentOrder> {

    FulfillmentOrder selectByWinningRecordIdForShare(
            @Param("winningRecordId") Long winningRecordId
    );

    long countByUser(
            @Param("userId") long userId,
            @Param("status") Integer status
    );

    List<FulfillmentOrder> selectPageByUser(
            @Param("userId") long userId,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    FulfillmentOrder selectByIdAndUser(
            @Param("orderId") long orderId,
            @Param("userId") long userId
    );

    long countForAdmin(@Param("status") Integer status);

    List<FulfillmentOrder> selectPageForAdmin(
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    FulfillmentOrder selectOrderById(@Param("orderId") long orderId);

    int claimPendingOrder(
            @Param("orderId") long orderId,
            @Param("userId") long userId,
            @Param("consigneeName") String consigneeName,
            @Param("consigneePhone") String consigneePhone,
            @Param("province") String province,
            @Param("city") String city,
            @Param("district") String district,
            @Param("detailAddress") String detailAddress,
            @Param("now") LocalDateTime now
    );

    int shipClaimedOrder(
            @Param("orderId") long orderId,
            @Param("logisticsCompany") String logisticsCompany,
            @Param("trackingNumber") String trackingNumber,
            @Param("now") LocalDateTime now
    );

    int receiveShippedOrder(
            @Param("orderId") long orderId,
            @Param("userId") long userId,
            @Param("now") LocalDateTime now
    );
}
