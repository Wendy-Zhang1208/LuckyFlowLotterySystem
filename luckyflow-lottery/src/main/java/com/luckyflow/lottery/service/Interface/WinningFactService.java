package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.model.WinningFact;

/** 向应用层提供中奖事实核对能力，隐藏 Lottery 模块内部 Mapper。 */
public interface WinningFactService {

    WinningFact findWinningFact(Long winningRecordId);
}
