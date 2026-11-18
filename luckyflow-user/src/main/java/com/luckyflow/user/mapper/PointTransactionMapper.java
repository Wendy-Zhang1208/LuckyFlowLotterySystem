package com.luckyflow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.user.entity.PointTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分账本流水的 MyBatis-Plus 数据访问接口。
 */
@Mapper
public interface PointTransactionMapper extends BaseMapper<PointTransaction> {

    PointTransaction selectByBizTypeAndBizIdForShare(
            @Param("bizType") String bizType,
            @Param("bizId") Long bizId
    );

    List<PointTransaction> selectPageByUserId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByUserId(@Param("userId") Long userId);
}
