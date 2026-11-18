package com.luckyflow.fulfillment.constant;

/**
 * 履约订单在数据库中存储的生命周期状态值。
 */
public final class FulfillmentOrderStatusConstants {

    public static final int FULFILLMENT_STATUS_PENDING_CLAIM = 0;
    public static final int FULFILLMENT_STATUS_CLAIMED = 1;
    public static final int FULFILLMENT_STATUS_SHIPPED = 2;
    public static final int FULFILLMENT_STATUS_RECEIVED = 3;
    public static final int FULFILLMENT_STATUS_COMPLETED = 4;
    public static final int FULFILLMENT_STATUS_EXPIRED = 5;

    /**
     * 工具类，不允许实例化。
     */
    private FulfillmentOrderStatusConstants() {
    }
}
