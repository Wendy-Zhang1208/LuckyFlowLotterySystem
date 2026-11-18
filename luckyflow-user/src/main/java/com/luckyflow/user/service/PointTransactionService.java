package com.luckyflow.user.service;

import com.luckyflow.user.entity.PointTransaction;

import java.util.List;

/**
 * 对外提供积分账本流水记录能力。
 */
public interface PointTransactionService {

    record TransactionPage(List<PointTransaction> records, long total) {
        public TransactionPage {
            records = records == null ? List.of() : List.copyOf(records);
            if (total < 0) {
                throw new IllegalArgumentException("total must not be negative");
            }
        }
    }

    boolean recordTransaction(PointTransaction pointTransaction);

    /** 按用户隔离读取积分账本，使用稳定时间和主键倒序支持一基分页。 */
    TransactionPage findTransactionsByUserId(Long userId, int page, int size);
}
