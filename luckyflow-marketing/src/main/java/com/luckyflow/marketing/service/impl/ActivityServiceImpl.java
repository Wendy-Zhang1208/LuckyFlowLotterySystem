package com.luckyflow.marketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luckyflow.exception.BusinessException;
import com.luckyflow.marketing.entity.Activity;
import com.luckyflow.marketing.mapper.ActivityMapper;
import com.luckyflow.marketing.service.ActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_DRAFT;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_ENDED;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_PENDING_REVIEW;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_PUBLISHED;
import static com.luckyflow.marketing.constant.ActivityStatusConstants.ACTIVITY_STATUS_RUNNING;
import static com.luckyflow.marketing.constant.ActivityThemeKeys.normalize;

/**
 * 营销活动领域服务实现，活动数据访问细节仅保留在 Marketing 模块内部。
 */
@Service
public class ActivityServiceImpl implements ActivityService {

    private static final int MAX_PUBLIC_PAGE_SIZE = 100;

    private static final String UNSUPPORTED_STATUS_TRANSITION_MESSAGE =
            "Current activity status does not support this status transition";

    private final ActivityMapper activityMapper;
    private final Clock clock;

    public ActivityServiceImpl(ActivityMapper activityMapper, Clock clock) {
        this.activityMapper = activityMapper;
        this.clock = clock;
    }

    @Override
    public Activity findActivity(Long activityId) {
        return activityMapper.selectById(activityId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Activity createDraft(Activity activity) {
        validateActivityConfiguration(activity, false);
        if (activity.getId() != null) {
            throw new IllegalArgumentException("New activity ID must be null");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        activity.setStatus(ACTIVITY_STATUS_DRAFT);
        activity.setCreateTime(now);
        activity.setUpdateTime(now);
        if (activityMapper.insert(activity) != 1 || activity.getId() == null) {
            throw new IllegalStateException("Failed to create activity draft");
        }
        return activity;
    }

    @Override
    public ActivityPage findActivities(String keyword, Integer status, int page, int size) {
        validatePage(page, size);
        if (status != null && (status < ACTIVITY_STATUS_DRAFT || status > ACTIVITY_STATUS_ENDED)) {
            throw new IllegalArgumentException("Unsupported activity status");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        int offset = Math.multiplyExact(page - 1, size);
        return new ActivityPage(
                activityMapper.selectAdminPage(normalizedKeyword, status, offset, size),
                activityMapper.countAdminPage(normalizedKeyword, status)
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Activity lockActivityForConfigurationEdit(Long activityId) {
        Activity activity = lockExistingActivity(activityId);
        if (!isConfigurationEditable(activity.getStatus())) {
            throw new BusinessException("Published activity configuration cannot be modified");
        }
        return activity;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Activity lockActivityForPublication(Long activityId) {
        Activity activity = lockExistingActivity(activityId);
        if (!Integer.valueOf(ACTIVITY_STATUS_PENDING_REVIEW).equals(activity.getStatus())) {
            throw new BusinessException("Only an activity pending review can be published");
        }
        validateActivityConfiguration(activity);
        return activity;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void updateActivityConfiguration(Activity activity) {
        validateActivityConfiguration(activity, true);
        lockActivityForConfigurationEdit(activity.getId());

        activity.setUpdateTime(LocalDateTime.now(clock));
        if (activityMapper.updateConfiguration(activity) != 1) {
            throw new IllegalStateException("Failed to update activity configuration");
        }
    }

    @Override
    public void updateActivityStatus(long activityId, int currentStatus, int targetStatus) {
        if (!isSupportedTransition(currentStatus, targetStatus)) {
            throw new BusinessException(UNSUPPORTED_STATUS_TRANSITION_MESSAGE);
        }

        // 将当前状态放入更新条件，防止并发状态变化后仍被旧流程覆盖。
        int updatedRows = activityMapper.update(
                null,
                Wrappers.<Activity>lambdaUpdate()
                        .eq(Activity::getId, activityId)
                        .eq(Activity::getStatus, currentStatus)
                        .set(Activity::getStatus, targetStatus)
        );
        if (updatedRows != 1) {
            throw new BusinessException(UNSUPPORTED_STATUS_TRANSITION_MESSAGE);
        }
    }

    @Override
    public PublicActivityPage findPublicOngoing(String keyword, int page, int size) {
        validatePage(page, size);
        String normalizedKeyword = normalizeKeyword(keyword);
        LocalDateTime now = LocalDateTime.now(clock);
        int offset = Math.multiplyExact(page - 1, size);
        List<Activity> records = activityMapper.selectPublicOngoingPage(
                normalizedKeyword,
                now,
                offset,
                size
        );
        return new PublicActivityPage(
                records,
                activityMapper.countPublicOngoing(normalizedKeyword, now)
        );
    }

    @Override
    public PublicActivityPage findPublicActivities(String status, String keyword, int page, int size) {
        validatePage(page, size);
        String normalizedKeyword = normalizeKeyword(keyword);
        LocalDateTime now = LocalDateTime.now(clock);
        int offset = Math.multiplyExact(page - 1, size);
        // 查询层只允许公开生命周期，草稿和待审核行不会因筛选 ALL 泄露。
        return new PublicActivityPage(
                activityMapper.selectPublicPage(normalizedKeyword, status, now, offset, size),
                activityMapper.countPublic(normalizedKeyword, status, now)
        );
    }

    @Override
    public Activity findPublicActivity(Long activityId) {
        requirePositiveId(activityId, "activityId");
        return activityMapper.selectPublicById(activityId);
    }

    @Override
    public boolean isCurrentlyDrawable(Activity activity) {
        if (activity == null
                || !Integer.valueOf(ACTIVITY_STATUS_RUNNING).equals(activity.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return activity.getStartTime() != null
                && activity.getEndTime() != null
                && !now.isBefore(activity.getStartTime())
                && now.isBefore(activity.getEndTime());
    }

    @Override
    public String resolveDisplayStatus(Activity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (Integer.valueOf(ACTIVITY_STATUS_ENDED).equals(activity.getStatus())
                || (activity.getEndTime() != null && !now.isBefore(activity.getEndTime()))) {
            return "ENDED";
        }
        if (Integer.valueOf(ACTIVITY_STATUS_RUNNING).equals(activity.getStatus())
                && activity.getStartTime() != null
                && !now.isBefore(activity.getStartTime())
                && activity.getEndTime() != null
                && now.isBefore(activity.getEndTime())) {
            return "ONGOING";
        }
        return "UPCOMING";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int synchronizeLifecycle(int batchSize) {
        if (batchSize <= 0 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int transitioned = 0;
        for (Long activityId : activityMapper.selectPublishedReadyToEnd(now, batchSize)) {
            transitioned += activityMapper.compareAndSetStatus(
                    activityId,
                    ACTIVITY_STATUS_PUBLISHED,
                    ACTIVITY_STATUS_ENDED,
                    now
            );
        }
        for (Long activityId : activityMapper.selectPublishedReadyToRun(now, batchSize)) {
            transitioned += activityMapper.compareAndSetStatus(
                    activityId,
                    ACTIVITY_STATUS_PUBLISHED,
                    ACTIVITY_STATUS_RUNNING,
                    now
            );
        }
        for (Long activityId : activityMapper.selectRunningReadyToEnd(now, batchSize)) {
            transitioned += activityMapper.compareAndSetStatus(
                    activityId,
                    ACTIVITY_STATUS_RUNNING,
                    ACTIVITY_STATUS_ENDED,
                    now
            );
        }
        return transitioned;
    }

    private boolean isSupportedTransition(int currentStatus, int targetStatus) {
        if (targetStatus == ACTIVITY_STATUS_ENDED) {
            return currentStatus == ACTIVITY_STATUS_DRAFT
                    || currentStatus == ACTIVITY_STATUS_PENDING_REVIEW
                    || currentStatus == ACTIVITY_STATUS_PUBLISHED
                    || currentStatus == ACTIVITY_STATUS_RUNNING;
        }

        return (currentStatus == ACTIVITY_STATUS_DRAFT
                && targetStatus == ACTIVITY_STATUS_PENDING_REVIEW)
                || (currentStatus == ACTIVITY_STATUS_PENDING_REVIEW
                && targetStatus == ACTIVITY_STATUS_PUBLISHED)
                || (currentStatus == ACTIVITY_STATUS_PUBLISHED
                && targetStatus == ACTIVITY_STATUS_RUNNING);
    }

    private Activity lockExistingActivity(Long activityId) {
        requirePositiveId(activityId, "activityId");
        Activity activity = activityMapper.selectByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException("Activity does not exist");
        }
        return activity;
    }

    private boolean isConfigurationEditable(Integer status) {
        return Integer.valueOf(ACTIVITY_STATUS_DRAFT).equals(status)
                || Integer.valueOf(ACTIVITY_STATUS_PENDING_REVIEW).equals(status);
    }

    private void validateActivityConfiguration(Activity activity) {
        validateActivityConfiguration(activity, true);
    }

    private void validateActivityConfiguration(Activity activity, boolean requireId) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity configuration must not be null");
        }
        if (requireId) {
            requirePositiveId(activity.getId(), "activityId");
        }
        if (activity.getName() == null || activity.getName().isBlank()) {
            throw new IllegalArgumentException("Activity name must not be blank");
        }
        if (activity.getSummary() == null) {
            activity.setSummary("");
        }
        if (activity.getSummary().length() > 500) {
            throw new IllegalArgumentException("Activity summary is too long");
        }
        activity.setThemeKey(normalize(activity.getThemeKey()));
        if (activity.getStartTime() == null || activity.getEndTime() == null
                || !activity.getStartTime().isBefore(activity.getEndTime())) {
            throw new IllegalArgumentException("Activity start time must be before end time");
        }
        if (activity.getDailyDrawLimit() == null || activity.getDailyDrawLimit() <= 0) {
            throw new IllegalArgumentException("Activity daily draw limit must be positive");
        }
        if (activity.getPointCost() == null || activity.getPointCost() < 0) {
            throw new IllegalArgumentException("Activity point cost must not be negative");
        }
        if (activity.getNoWinWeight() == null || activity.getNoWinWeight() < 0) {
            throw new IllegalArgumentException("Activity no-win weight must not be negative");
        }
    }

    private void requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PUBLIC_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "page must be positive and size must be between 1 and 100"
            );
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("keyword is too long");
        }
        return normalized;
    }
}
