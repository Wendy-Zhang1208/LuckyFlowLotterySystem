package com.luckyflow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.user.entity.PointAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户积分账户的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface PointAccountMapper extends BaseMapper<PointAccount> {

    /**
     * 在账户存在且余额充足时原子扣减积分。
     *
     * <p>余额条件与扣减操作有意放在同一条 SQL 中，以避免应用层先检查后更新的并发竞态。</p>
     *
     * @param userId 账户所属用户标识
     * @param cost 本次需要扣减的正积分数
     * @return 受影响行数：扣减成功为 {@code 1}，否则为 {@code 0}
     */
    @Update("UPDATE point_account "
            + "SET balance = balance - #{cost} "
            + "WHERE user_id = #{userId} "
            + "AND balance >= #{cost}")
    int deductPoints(@Param("userId") Long userId, @Param("cost") Integer cost);

    /**
     * 锁定积分账户，保证并发奖励到账时余额和流水的 afterBalance 按真实入账顺序计算。
     */
    @Select("SELECT id, user_id, balance, create_time, update_time "
            + "FROM point_account WHERE user_id = #{userId} FOR UPDATE")
    PointAccount selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 按已锁定账户的旧余额执行条件更新；条件失配必须由上层视为事务失败。
     */
    @Update("UPDATE point_account "
            + "SET balance = balance + #{points}, update_time = #{updateTime} "
            + "WHERE id = #{accountId} AND balance = #{expectedBalance}")
    int addPoints(
            @Param("accountId") Long accountId,
            @Param("expectedBalance") Long expectedBalance,
            @Param("points") long points,
            @Param("updateTime") LocalDateTime updateTime
    );
}
