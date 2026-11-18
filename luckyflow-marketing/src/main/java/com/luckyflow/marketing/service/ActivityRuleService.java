package com.luckyflow.marketing.service;

import com.luckyflow.marketing.entity.ActivityRule;

import java.util.List;

/** 提供活动详情所需的有序公开规则。 */
public interface ActivityRuleService {

    List<ActivityRule> findByActivityId(Long activityId);

    /** 用一组有序规则替换草稿/待审核活动的全部展示规则。 */
    List<ActivityRule> replaceRules(Long activityId, List<ActivityRule> rules);
}
