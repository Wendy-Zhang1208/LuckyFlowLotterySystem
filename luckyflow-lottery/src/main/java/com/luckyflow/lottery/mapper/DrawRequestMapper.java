package com.luckyflow.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.lottery.entity.DrawRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DrawRequestMapper extends BaseMapper<DrawRequest> {

    /**
     * 幂等登记抽奖请求；返回 1 表示本次创建，返回 0 表示 requestId 已存在。
     */
    int insertIfAbsent(@Param("request") DrawRequest request);

    /** 调用方必须在事务内完成读取和后续条件状态迁移。 */
    DrawRequest selectByRequestIdForUpdate(@Param("requestId") String requestId);

    int markSuccess(
            @Param("requestId") String requestId,
            @Param("executionToken") String executionToken,
            @Param("resultPayload") String resultPayload,
            @Param("completedTime") LocalDateTime completedTime
    );

    int markCancelled(
            @Param("requestId") String requestId,
            @Param("executionToken") String executionToken,
            @Param("completedTime") LocalDateTime completedTime
    );

    /**
     * 锁定一批需要恢复的请求标识，供多实例恢复任务使用。
     */
    List<String> selectRecoverableRequestIdsForUpdate(
            @Param("executionCutoff") LocalDateTime executionCutoff,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    DrawRequest selectLatestPendingByOwnerAndActivity(
            @Param("userId") Long userId,
            @Param("activityId") Long activityId
    );
}
