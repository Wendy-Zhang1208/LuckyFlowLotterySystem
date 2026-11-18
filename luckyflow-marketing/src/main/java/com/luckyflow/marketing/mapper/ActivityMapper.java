package com.luckyflow.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.marketing.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 营销活动的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {

    /** 锁定活动配置，作为发布与运营编辑共同遵守的并发互斥点。 */
    @Select("SELECT id, name, summary, theme_key, start_time, end_time, daily_draw_limit, point_cost, "
            + "no_win_weight, status, create_time, update_time "
            + "FROM activity WHERE id = #{activityId} FOR UPDATE")
    Activity selectByIdForUpdate(@Param("activityId") Long activityId);

    /** 仅修改抽奖规则字段，状态迁移继续由独立的生命周期方法负责。 */
    @Update("UPDATE activity SET "
            + "name = #{activity.name}, "
            + "summary = #{activity.summary}, "
            + "theme_key = #{activity.themeKey}, "
            + "start_time = #{activity.startTime}, "
            + "end_time = #{activity.endTime}, "
            + "daily_draw_limit = #{activity.dailyDrawLimit}, "
            + "point_cost = #{activity.pointCost}, "
            + "no_win_weight = #{activity.noWinWeight}, "
            + "update_time = #{activity.updateTime} "
            + "WHERE id = #{activity.id} AND status IN (0, 1)")
    int updateConfiguration(@Param("activity") Activity activity);

    List<Activity> selectPublicOngoingPage(
            @Param("keyword") String keyword,
            @Param("now") LocalDateTime now,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countPublicOngoing(
            @Param("keyword") String keyword,
            @Param("now") LocalDateTime now
    );

    List<Activity> selectPublicPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countPublic(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("now") LocalDateTime now
    );

    Activity selectPublicById(@Param("activityId") Long activityId);

    List<Long> selectPublishedReadyToRun(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    List<Long> selectPublishedReadyToEnd(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    List<Long> selectRunningReadyToEnd(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    @Update("UPDATE activity SET status = #{targetStatus}, update_time = #{now} "
            + "WHERE id = #{activityId} AND status = #{currentStatus}")
    int compareAndSetStatus(
            @Param("activityId") Long activityId,
            @Param("currentStatus") int currentStatus,
            @Param("targetStatus") int targetStatus,
            @Param("now") LocalDateTime now
    );

    List<Activity> selectAdminPage(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdminPage(
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );
}
