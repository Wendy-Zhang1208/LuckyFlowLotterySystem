package com.luckyflow.marketing.service;

import com.luckyflow.marketing.entity.Activity;

import java.util.List;

/**
 * 对外提供营销活动查询与生命周期流转能力，避免其他业务模块直接访问活动 Mapper。
 */
public interface ActivityService {

    record PublicActivityPage(List<Activity> records, long total) {
        public PublicActivityPage {
            records = records == null ? List.of() : List.copyOf(records);
            if (total < 0) {
                throw new IllegalArgumentException("total must not be negative");
            }
        }
    }

    record ActivityPage(List<Activity> records, long total) {
        public ActivityPage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    Activity findActivity(Long activityId);

    Activity createDraft(Activity activity);

    ActivityPage findActivities(String keyword, Integer status, int page, int size);

    /**
     * 锁定并返回允许运营编辑的活动。调用方必须已经开启事务，锁会持有到该事务结束。
     */
    Activity lockActivityForConfigurationEdit(Long activityId);

    /**
     * 锁定待发布活动，发布流程据此阻止奖池配置在 Redis 初始化期间发生变化。
     */
    Activity lockActivityForPublication(Long activityId);

    /**
     * 修改活动抽奖规则；生命周期状态由 {@link #updateActivityStatus(long, int, int)} 单独维护。
     */
    void updateActivityConfiguration(Activity activity);

    /**
     * 在数据库当前状态仍等于 {@code currentStatus} 时迁移活动状态。
     */
    void updateActivityStatus(long activityId, int currentStatus, int targetStatus);

    /** 查询服务器当前时间下真实可抽的 RUNNING 活动。 */
    PublicActivityPage findPublicOngoing(String keyword, int page, int size);

    /** 公开列表仅包含已发布及其后续状态，筛选状态按服务器时间计算。 */
    PublicActivityPage findPublicActivities(String status, String keyword, int page, int size);

    /** 查询允许公开展示的 PUBLISHED/RUNNING/ENDED 活动。 */
    Activity findPublicActivity(Long activityId);

    /** 与抽奖写链路一致地判断活动是否处于真实可抽状态。 */
    boolean isCurrentlyDrawable(Activity activity);

    /** 解析供公共页面展示的服务器侧状态。 */
    String resolveDisplayStatus(Activity activity);

    /** 按服务器时间以 CAS 推进 PUBLISHED/RUNNING 生命周期。 */
    int synchronizeLifecycle(int batchSize);
}
