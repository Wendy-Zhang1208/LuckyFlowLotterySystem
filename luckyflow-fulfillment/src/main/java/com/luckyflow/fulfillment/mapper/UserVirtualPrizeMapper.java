package com.luckyflow.fulfillment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luckyflow.fulfillment.entity.UserVirtualPrize;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 虚拟奖品持久化访问，仅供 Fulfillment 模块内部使用。 */
@Mapper
public interface UserVirtualPrizeMapper extends BaseMapper<UserVirtualPrize> {

    UserVirtualPrize selectByWinningRecordIdForShare(
            @Param("winningRecordId") Long winningRecordId
    );
}
