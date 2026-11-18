package com.luckyflow.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.marketing.entity.Prize;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 奖品目录记录的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface PrizeMapper extends BaseMapper<Prize> {

    /** 发布复制业务快照与目录编辑共用该行锁，避免读到被并发改写一半的奖品定义。 */
    @Select("SELECT id, name, description, illustration, type, total_inventory, available_inventory, value, status, "
            + "create_time, update_time FROM prize WHERE id = #{prizeId} FOR UPDATE")
    Prize selectByIdForUpdate(@Param("prizeId") Long prizeId);

    /** 奖品行锁取得后，以 READ COMMITTED 的新读视图检查已提交的历史发布快照；本查询不锁奖池行。 */
    @Select("SELECT EXISTS(SELECT 1 FROM activity_prize "
            + "WHERE prize_id = #{prizeId} AND prize_type_snapshot IS NOT NULL)")
    boolean hasPublishedReference(@Param("prizeId") Long prizeId);

    /** 统计所有活动的分配量，包含停用配置；持有 Prize 行锁时不反向锁奖池行。 */
    @Select("SELECT COALESCE(SUM(allocated_inventory), 0) FROM activity_prize WHERE prize_id = #{prizeId}")
    long sumAllocatedInventory(@Param("prizeId") Long prizeId);

    @Update("UPDATE prize SET "
            + "name = #{prize.name}, "
            + "description = #{prize.description}, "
            + "illustration = #{prize.illustration}, "
            + "type = #{prize.type}, "
            + "total_inventory = #{prize.totalInventory}, "
            + "available_inventory = #{prize.availableInventory}, "
            + "value = #{prize.value}, "
            + "status = #{prize.status}, "
            + "update_time = #{prize.updateTime} "
            + "WHERE id = #{prize.id}")
    int updateConfiguration(@Param("prize") Prize prize);

    List<Prize> selectAdminPage(
            @Param("keyword") String keyword,
            @Param("type") Integer type,
            @Param("status") Integer status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countAdminPage(
            @Param("keyword") String keyword,
            @Param("type") Integer type,
            @Param("status") Integer status
    );
}
