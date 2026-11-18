package com.luckyflow.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.mapper.PointAccountMapper;
import com.luckyflow.user.service.PointAccountService;
import org.springframework.stereotype.Service;

/**
 * 积分账户服务实现，条件扣减 SQL 继续由 User 模块内部 Mapper 执行。
 */
@Service
public class PointAccountServiceImpl implements PointAccountService {

    private final PointAccountMapper pointAccountMapper;

    public PointAccountServiceImpl(PointAccountMapper pointAccountMapper) {
        this.pointAccountMapper = pointAccountMapper;
    }

    @Override
    public boolean tryDeductPoints(Long userId, int cost) {
        return pointAccountMapper.deductPoints(userId, cost) == 1;
    }

    @Override
    public PointAccount findAccountByUserId(Long userId) {
        return pointAccountMapper.selectOne(
                Wrappers.<PointAccount>lambdaQuery()
                        .eq(PointAccount::getUserId, userId)
        );
    }
}
