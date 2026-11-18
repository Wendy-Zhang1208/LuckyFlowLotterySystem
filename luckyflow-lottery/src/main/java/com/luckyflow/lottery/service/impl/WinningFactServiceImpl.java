package com.luckyflow.lottery.service.impl;

import com.luckyflow.lottery.mapper.WinningRecordMapper;
import com.luckyflow.lottery.model.WinningFact;
import com.luckyflow.lottery.service.Interface.WinningFactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 在消费事务中读取数据库中奖事实。 */
@Service
public class WinningFactServiceImpl implements WinningFactService {

    private final WinningRecordMapper winningRecordMapper;

    public WinningFactServiceImpl(WinningRecordMapper winningRecordMapper) {
        this.winningRecordMapper = winningRecordMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public WinningFact findWinningFact(Long winningRecordId) {
        return winningRecordMapper.selectWinningFactForShare(winningRecordId);
    }
}
