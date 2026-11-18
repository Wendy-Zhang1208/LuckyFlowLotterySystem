package com.luckyflow.user.service.impl;

import com.luckyflow.user.entity.PointTransaction;
import com.luckyflow.user.mapper.PointTransactionMapper;
import com.luckyflow.user.service.PointTransactionService;
import org.springframework.stereotype.Service;

/**
 * 积分账本流水服务实现，负责将流水 Mapper 限定在 User 模块内部。
 */
@Service
public class PointTransactionServiceImpl implements PointTransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PointTransactionMapper pointTransactionMapper;

    public PointTransactionServiceImpl(PointTransactionMapper pointTransactionMapper) {
        this.pointTransactionMapper = pointTransactionMapper;
    }

    @Override
    public boolean recordTransaction(PointTransaction pointTransaction) {
        return pointTransactionMapper.insert(pointTransaction) == 1;
    }

    @Override
    public TransactionPage findTransactionsByUserId(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "page must be positive and size must be between 1 and 100"
            );
        }
        int offset = Math.multiplyExact(page - 1, size);
        return new TransactionPage(
                pointTransactionMapper.selectPageByUserId(userId, offset, size),
                pointTransactionMapper.countByUserId(userId)
        );
    }
}
