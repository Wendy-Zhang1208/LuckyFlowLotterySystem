package com.luckyflow.marketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luckyflow.marketing.entity.ActivityRule;
import com.luckyflow.marketing.mapper.ActivityRuleMapper;
import com.luckyflow.marketing.service.ActivityService;
import com.luckyflow.marketing.service.ActivityRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 活动规则只按显式排序号和主键稳定读取。 */
@Service
public class ActivityRuleServiceImpl implements ActivityRuleService {

    private final ActivityRuleMapper activityRuleMapper;
    private final ActivityService activityService;
    private final Clock clock;

    public ActivityRuleServiceImpl(
            ActivityRuleMapper activityRuleMapper,
            ActivityService activityService,
            Clock clock
    ) {
        this.activityRuleMapper = activityRuleMapper;
        this.activityService = activityService;
        this.clock = clock;
    }

    @Override
    public List<ActivityRule> findByActivityId(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId must be positive");
        }
        return activityRuleMapper.selectList(
                Wrappers.<ActivityRule>lambdaQuery()
                        .eq(ActivityRule::getActivityId, activityId)
                        .orderByAsc(ActivityRule::getSortOrder)
                        .orderByAsc(ActivityRule::getId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ActivityRule> replaceRules(Long activityId, List<ActivityRule> rules) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId must be positive");
        }
        List<ActivityRule> source = rules == null ? List.of() : List.copyOf(rules);
        if (source.size() > 50) {
            throw new IllegalArgumentException("Activity rules must not exceed 50 items");
        }
        // 与奖池编辑共用活动行锁，发布冻结期间不能换入另一版展示规则。
        activityService.lockActivityForConfigurationEdit(activityId);
        activityRuleMapper.delete(
                Wrappers.<ActivityRule>lambdaQuery().eq(ActivityRule::getActivityId, activityId)
        );

        LocalDateTime now = LocalDateTime.now(clock);
        List<ActivityRule> created = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            ActivityRule input = source.get(index);
            validateRule(input);
            ActivityRule rule = new ActivityRule();
            rule.setActivityId(activityId);
            rule.setTitle(input.getTitle().trim());
            rule.setContent(input.getContent().trim());
            rule.setSortOrder(index);
            rule.setCreateTime(now);
            rule.setUpdateTime(now);
            if (activityRuleMapper.insert(rule) != 1 || rule.getId() == null) {
                throw new IllegalStateException("Failed to save activity rule");
            }
            created.add(rule);
        }
        return List.copyOf(created);
    }

    private void validateRule(ActivityRule rule) {
        if (rule == null || rule.getTitle() == null || rule.getTitle().isBlank()
                || rule.getContent() == null || rule.getContent().isBlank()) {
            throw new IllegalArgumentException("Activity rule title and content must not be blank");
        }
        if (rule.getTitle().trim().length() > 100 || rule.getContent().trim().length() > 1000) {
            throw new IllegalArgumentException("Activity rule is too long");
        }
    }
}
