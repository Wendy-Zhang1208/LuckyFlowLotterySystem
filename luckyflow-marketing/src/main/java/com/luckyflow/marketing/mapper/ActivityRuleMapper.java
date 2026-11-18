package com.luckyflow.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.marketing.entity.ActivityRule;
import org.apache.ibatis.annotations.Mapper;

/** 活动展示规则的数据访问接口。 */
@Mapper
public interface ActivityRuleMapper extends BaseMapper<ActivityRule> {
}
