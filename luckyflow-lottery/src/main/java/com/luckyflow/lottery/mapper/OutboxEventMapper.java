package com.luckyflow.lottery.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.lottery.entity.OutboxEvent;


@Mapper 
public interface OutboxEventMapper extends BaseMapper<OutboxEvent>{
    
        List<OutboxEvent> selectPendingForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
        );
    
        int markProcessing(
            @Param("id") Long id,
            @Param("processingToken") String processingToken,
            @Param("processingTime") LocalDateTime processingTime
        );

        int markPublished(
            @Param("id") Long id,
            @Param("processingToken") String processingToken,
            @Param("retryCount") int retryCount,
            @Param("publishTime") LocalDateTime publishTime
        );

        int markPublishFailed(
            @Param("id") Long id,
            @Param("processingToken") String processingToken,
            @Param("retryCount") int retryCount,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
        );

        List<OutboxEvent> selectTimedOutProcessingForUpdate(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
        );
} 

