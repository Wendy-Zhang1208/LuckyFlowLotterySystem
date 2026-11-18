package com.luckyflow.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.lottery.entity.DrawResourceReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DrawResourceReservationMapper extends BaseMapper<DrawResourceReservation> {

    DrawResourceReservation findByIntent(
            @Param("requestId") String requestId,
            @Param("resourceType") int resourceType,
            @Param("attemptNo") int attemptNo
    );

    int markReserved(
            @Param("id") Long id,
            @Param("reservedTime") LocalDateTime reservedTime
    );

    int markNotReserved(
            @Param("id") Long id,
            @Param("resolvedTime") LocalDateTime resolvedTime
    );

    List<DrawResourceReservation> selectPendingCompensation(
            @Param("requestId") String requestId,
            @Param("now") LocalDateTime now
    );

    int countUnsafeCancellationStates(@Param("requestId") String requestId);

    int markCompensated(
            @Param("id") Long id,
            @Param("compensatedTime") LocalDateTime compensatedTime
    );

    int markCompensationFailed(
            @Param("id") Long id,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );

    int confirmReservedByRequestId(
            @Param("requestId") String requestId,
            @Param("confirmedTime") LocalDateTime confirmedTime
    );

    List<DrawResourceReservation> selectPendingConfirmation(
            @Param("requestId") String requestId,
            @Param("now") LocalDateTime now
    );

    int markConfirmationCompleted(
            @Param("id") Long id,
            @Param("terminalizedTime") LocalDateTime terminalizedTime
    );

    int markConfirmationFailed(
            @Param("id") Long id,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );
}
