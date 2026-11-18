package com.luckyflow.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.marketing.entity.ActivityPrize;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动奖品配置的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface ActivityPrizeMapper extends BaseMapper<ActivityPrize> {

    @Select("SELECT id, activity_id, prize_id, prize_type_snapshot, prize_value_snapshot, "
            + "weight, allocated_inventory, remaining_inventory, status, create_time, update_time "
            + "FROM activity_prize WHERE id = #{activityPrizeId} FOR UPDATE")
    ActivityPrize selectByIdForUpdate(@Param("activityPrizeId") Long activityPrizeId);

    /**
     * 活动行锁取得后再按主键顺序锁定奖池，发布和运营编辑因此观察到同一稳定配置。
     */
    @Select("SELECT id, activity_id, prize_id, prize_type_snapshot, prize_value_snapshot, "
            + "weight, allocated_inventory, remaining_inventory, status, create_time, update_time "
            + "FROM activity_prize WHERE activity_id = #{activityId} ORDER BY id FOR UPDATE")
    List<ActivityPrize> selectByActivityIdForUpdate(@Param("activityId") Long activityId);

    /** 编辑活动奖品时清空旧快照，正式快照只能由发布流程重新生成。 */
    @Update("UPDATE activity_prize SET "
            + "prize_id = #{activityPrize.prizeId}, "
            + "prize_type_snapshot = NULL, "
            + "prize_value_snapshot = NULL, "
            + "weight = #{activityPrize.weight}, "
            + "allocated_inventory = #{activityPrize.allocatedInventory}, "
            + "remaining_inventory = #{activityPrize.allocatedInventory}, "
            + "status = #{activityPrize.status}, "
            + "update_time = #{activityPrize.updateTime} "
            + "WHERE id = #{activityPrize.id} AND activity_id = #{activityId}")
    int updateConfiguration(
            @Param("activityId") Long activityId,
            @Param("activityPrize") ActivityPrize activityPrize
    );

    @Delete("DELETE FROM activity_prize "
            + "WHERE id = #{activityPrizeId} AND activity_id = #{activityId}")
    int deleteConfiguration(
            @Param("activityId") Long activityId,
            @Param("activityPrizeId") Long activityPrizeId
    );

    /** 调用方已锁定 Prize；原子条件防止目录库存不足时继续划拨。 */
    @Update("UPDATE prize SET available_inventory = available_inventory - #{quantity}, "
            + "update_time = #{updateTime} "
            + "WHERE id = #{prizeId} AND #{quantity} > 0 AND available_inventory >= #{quantity}")
    int allocatePrizeInventory(
            @Param("prizeId") Long prizeId,
            @Param("quantity") int quantity,
            @Param("updateTime") LocalDateTime updateTime
    );

    /** 归还与奖池修改共用事务；先用减法校验上限，避免可用量超过目录总量。 */
    @Update("UPDATE prize SET available_inventory = available_inventory + #{quantity}, "
            + "update_time = #{updateTime} "
            + "WHERE id = #{prizeId} AND #{quantity} > 0 "
            + "AND available_inventory <= total_inventory - #{quantity}")
    int releasePrizeInventory(
            @Param("prizeId") Long prizeId,
            @Param("quantity") int quantity,
            @Param("updateTime") LocalDateTime updateTime
    );

    /** 将已经锁定的共享奖品定义固化到当前活动奖品，影响行数必须严格为一。 */
    @Update("UPDATE activity_prize SET "
            + "prize_type_snapshot = #{prizeType}, "
            + "prize_value_snapshot = #{prizeValue}, "
            + "update_time = #{updateTime} "
            + "WHERE id = #{activityPrizeId} "
            + "AND activity_id = #{activityId} "
            + "AND prize_id = #{prizeId} "
            + "AND status = 1")
    int freezePrizeSnapshot(
            @Param("activityId") Long activityId,
            @Param("activityPrizeId") Long activityPrizeId,
            @Param("prizeId") Long prizeId,
            @Param("prizeType") Integer prizeType,
            @Param("prizeValue") BigDecimal prizeValue,
            @Param("updateTime") LocalDateTime updateTime
    );

    /**
     * 仅在库存大于零时原子扣减一个库存单位。
     *
     * @param activityPrizeId 活动奖品配置标识
     * @return 受影响行数：扣减成功为 {@code 1}，否则为 {@code 0}
     */
    @Update("UPDATE activity_prize "
            + "SET remaining_inventory = remaining_inventory - 1 "
            + "WHERE id = #{activityPrizeId} "
            + "AND remaining_inventory > 0")
    int deductInventory(@Param("activityPrizeId") Long activityPrizeId);
}
