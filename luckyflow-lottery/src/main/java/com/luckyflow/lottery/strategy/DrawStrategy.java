package com.luckyflow.lottery.strategy;

import java.util.List;
import java.util.Optional;

import com.luckyflow.lottery.model.DrawCandidate;

/**
 * 从内存候选奖品快照中选择奖品的策略接口。
 */
public interface DrawStrategy {

    /**
     * 按照具体实现的权重规则选择候选奖品。
     *
     * @param candidates 候选奖品配置
     * @param noWinWeight 未中奖区间所占权重
     * @return 选中的候选奖品；未中奖时返回空结果
     */
    Optional<DrawCandidate> draw(List<DrawCandidate> candidates, int noWinWeight);
}
