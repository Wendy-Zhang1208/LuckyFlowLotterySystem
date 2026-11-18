package com.luckyflow.lottery.constant;

/**
 * 抽奖资源预占协议使用的全局 reservation key 与状态值。
 *
 * <p>reservation key 不再从具体资源 key 派生。其 value 同时绑定资源类型、资源 key
 * 和当前状态，使同一个 reservationId 不能被误用于另一份每日次数或奖品库存。</p>
 */
public final class DrawReservationRedisKeys {

    public static final String RESOURCE_TYPE_DAILY_DRAW_LIMIT =
            Integer.toString(DrawResourceTypeConstants.DAILY_DRAW_LIMIT);
    public static final String RESOURCE_TYPE_PRIZE_INVENTORY =
            Integer.toString(DrawResourceTypeConstants.PRIZE_INVENTORY);

    public static final String STATUS_RESERVED = "RESERVED";
    /** MySQL 请求已经提交成功，资源消耗成为不可补偿的正式事实。 */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final String RESERVATION_KEY_PREFIX = "luckyflow:draw:reservation:";
    private static final String VALUE_SEPARATOR = "|";

    private DrawReservationRedisKeys() {
    }

    /** 构造由数据库 reservationId 唯一标识的全局 reservation key。 */
    public static String reservationKey(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("Invalid reservationId");
        }
        return RESERVATION_KEY_PREFIX + reservationId;
    }

    /** 判断调用方传入的 key 是否属于全局 reservation 命名空间。 */
    public static boolean isReservationKey(String reservationKey) {
        return reservationKey != null
                && reservationKey.length() > RESERVATION_KEY_PREFIX.length()
                && reservationKey.startsWith(RESERVATION_KEY_PREFIX);
    }

    /**
     * 构造 Lua 保存的协议值；完整资源 key 参与绑定，避免 reservation 跨资源复用。
     */
    public static String reservationValue(String resourceType, String resourceKey, String status) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Invalid reservation resourceType");
        }
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("Invalid reservation resourceKey");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Invalid reservation status");
        }
        return resourceType + VALUE_SEPARATOR + resourceKey + VALUE_SEPARATOR + status;
    }
}
