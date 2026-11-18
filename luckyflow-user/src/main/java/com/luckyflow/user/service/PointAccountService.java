package com.luckyflow.user.service;

import com.luckyflow.user.entity.PointAccount;

/**
 * 对外提供积分账户查询和扣减能力，隐藏 User 模块内部的数据访问实现。
 */
public interface PointAccountService {

    /**
     * 在账户存在且余额充足时原子扣减积分。
     */
    boolean tryDeductPoints(Long userId, int cost);

    PointAccount findAccountByUserId(Long userId);
}
