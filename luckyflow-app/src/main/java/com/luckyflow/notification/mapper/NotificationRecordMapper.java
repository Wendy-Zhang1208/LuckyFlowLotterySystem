package com.luckyflow.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.notification.entity.NotificationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** notification_record 的数据访问接口。 */
@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecord> {

    /** 唯一键冲突后执行锁定当前读，用于核验已有通知是否属于同一业务事实。 */
    NotificationRecord selectByWinningRecordIdAndChannelForShare(
            @Param("winningRecordId") Long winningRecordId,
            @Param("channel") int channel
    );

    /** 锁定一批已到发送时间的待处理通知，不等待其他调度实例已经持有的行锁。 */
    List<NotificationRecord> selectPendingForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /** 使用独立 token 将待发送通知迁移为当前发送 attempt。 */
    int markProcessing(
            @Param("id") Long id,
            @Param("processingToken") String processingToken,
            @Param("processingTime") LocalDateTime processingTime
    );

    /**
     * 发送线程真正开始外部调用前刷新处理时间；状态、token 和重试次数共同阻止已失效的排队任务发送。
     */
    int markSendingStarted(
            @Param("id") Long id,
            @Param("processingToken") String processingToken,
            @Param("expectedRetryCount") int expectedRetryCount,
            @Param("processingTime") LocalDateTime processingTime
    );

    /** 将实际获得服务商成功响应的通知迁移到不可逆成功终态。 */
    int markSucceeded(
            @Param("id") Long id,
            @Param("sendTime") LocalDateTime sendTime,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    /** 仅由当前 token 对应的发送 attempt 记录失败并推进重试状态。 */
    int markFailed(
            @Param("id") Long id,
            @Param("processingToken") String processingToken,
            @Param("expectedRetryCount") int expectedRetryCount,
            @Param("targetStatus") int targetStatus,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    /** 锁定一批已超过处理时限的发送 attempt，供恢复事务推进失败次数。 */
    List<NotificationRecord> selectTimedOutProcessingForUpdate(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );

    /** 管理端只读分页，使用显式 LIMIT，避免依赖未配置的分页拦截器。 */
    List<NotificationRecord> selectAdminPage(
            @Param("status") Integer status,
            @Param("channel") Integer channel,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    long countAdminPage(
            @Param("status") Integer status,
            @Param("channel") Integer channel
    );
}
