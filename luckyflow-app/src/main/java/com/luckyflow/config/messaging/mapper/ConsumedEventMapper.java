package com.luckyflow.config.messaging.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.config.messaging.entity.ConsumedEvent;

/** consumed_event 表的数据访问接口。 */
@Mapper
public interface ConsumedEventMapper extends BaseMapper<ConsumedEvent> {
}
