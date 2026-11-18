package com.luckyflow.fulfillment.service;

import com.luckyflow.fulfillment.entity.FulfillmentOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外提供物理奖品履约订单初始化能力。
 */
public interface FulfillmentService {

    /**
     * 为已经成立的中奖记录初始化待领取履约订单。
     *
     * @param winningRecordId 中奖记录 ID
     * @param userId          获奖用户 ID
     * @param now             当前业务时间
     */
    void initializePhysicalPrizeOrder(Long winningRecordId, Long userId, LocalDateTime now);

    FulfillmentOrderPage findMyOrders(long userId, int page, int size, String status);

    FulfillmentOrder findMyOrder(long userId, long orderId);

    FulfillmentOrder claimMyOrder(long userId, long orderId, ShippingAddress shippingAddress);

    FulfillmentOrder receiveMyOrder(long userId, long orderId);

    FulfillmentOrderPage findAdminOrders(int page, int size, String status);

    FulfillmentOrder findAdminOrder(long orderId);

    FulfillmentOrder shipOrder(long orderId, ShippingInformation shippingInformation);

    /** 收货信息只在领取命令中使用，调用方不得提供 userId 或订单状态。 */
    record ShippingAddress(
            String consigneeName,
            String consigneePhone,
            String province,
            String city,
            String district,
            String detailAddress
    ) {
    }

    /** 运营单笔发货所需的真实物流字段。 */
    record ShippingInformation(String logisticsCompany, String trackingNumber) {
    }

    /** 领域查询分页结果，HTTP 层再负责把 Long 标识转换为十进制字符串。 */
    record FulfillmentOrderPage(
            List<FulfillmentOrder> records,
            long page,
            long size,
            long total
    ) {
        public FulfillmentOrderPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }
}
