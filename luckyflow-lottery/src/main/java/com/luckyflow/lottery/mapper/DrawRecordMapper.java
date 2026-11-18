package com.luckyflow.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.lottery.entity.DrawRecord;
import com.luckyflow.lottery.model.DrawHistoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 抽奖尝试记录的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface DrawRecordMapper extends BaseMapper<DrawRecord> {

    List<DrawHistoryEntry> selectHistoryByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countHistoryByUserId(@Param("userId") Long userId);
}
