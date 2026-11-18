package com.luckyflow.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.lottery.entity.WinningRecord;
import com.luckyflow.lottery.model.WinningFact;
import com.luckyflow.lottery.model.WinningHistoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 中奖记录的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface WinningRecordMapper extends BaseMapper<WinningRecord> {

    WinningFact selectWinningFactForShare(
            @Param("winningRecordId") Long winningRecordId
    );

    List<WinningHistoryEntry> selectHistoryByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countHistoryByUserId(@Param("userId") Long userId);
}
