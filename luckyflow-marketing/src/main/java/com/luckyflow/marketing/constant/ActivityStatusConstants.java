package com.luckyflow.marketing.constant;

/**
 * 营销活动在数据库中存储的生命周期状态值。
 */
public final class ActivityStatusConstants {

    public static final int ACTIVITY_STATUS_DRAFT = 0;
    public static final int ACTIVITY_STATUS_PENDING_REVIEW = 1;
    public static final int ACTIVITY_STATUS_PUBLISHED = 2;
    public static final int ACTIVITY_STATUS_RUNNING = 3;
    public static final int ACTIVITY_STATUS_ENDED = 4;

    /**
     * 工具类，不允许实例化。
     */
    private ActivityStatusConstants() {
    }
}
