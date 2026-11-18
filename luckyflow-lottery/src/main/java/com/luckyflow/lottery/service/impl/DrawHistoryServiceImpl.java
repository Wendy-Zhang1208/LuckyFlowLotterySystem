package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.mapper.DrawRecordMapper;
import com.luckyflow.lottery.mapper.WinningRecordMapper;
import com.luckyflow.lottery.service.Interface.DrawHistoryService;
import org.springframework.stereotype.Service;

/** Lottery 模块内部完成历史事实查询，调用方无需跨模块直接依赖 Mapper。 */
@Service
public class DrawHistoryServiceImpl implements DrawHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DrawRecordMapper drawRecordMapper;
    private final WinningRecordMapper winningRecordMapper;

    public DrawHistoryServiceImpl(
            DrawRecordMapper drawRecordMapper,
            WinningRecordMapper winningRecordMapper
    ) {
        this.drawRecordMapper = drawRecordMapper;
        this.winningRecordMapper = winningRecordMapper;
    }

    @Override
    public DrawHistoryPage findDrawHistory(Long userId, int page, int size) {
        int offset = validateAndOffset(userId, page, size);
        return new DrawHistoryPage(
                drawRecordMapper.selectHistoryByUserId(userId, offset, size),
                drawRecordMapper.countHistoryByUserId(userId)
        );
    }

    @Override
    public WinningHistoryPage findWinningHistory(Long userId, int page, int size) {
        int offset = validateAndOffset(userId, page, size);
        return new WinningHistoryPage(
                winningRecordMapper.selectHistoryByUserId(userId, offset, size),
                winningRecordMapper.countHistoryByUserId(userId)
        );
    }

    private int validateAndOffset(Long userId, int page, int size) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "page must be positive and size must be between 1 and 100"
            );
        }
        return Math.multiplyExact(page - 1, size);
    }
}
