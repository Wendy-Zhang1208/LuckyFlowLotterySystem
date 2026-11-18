package com.luckyflow.lottery.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;

import org.springframework.stereotype.Component;

import com.luckyflow.lottery.model.DrawCandidate;

/**
 * 使用整数累计权重区间选择奖品。
 *
 * <p>零权重或无库存的候选奖品不参与抽奖；配置的未中奖权重位于全部有效奖品区间之后。</p>
 */
@Component
public class WeightedDrawStrategy implements DrawStrategy {

    private final LongUnaryOperator randomGenerator;

    /**
     * 创建使用线程本地随机源的生产抽奖策略。
     */
    public WeightedDrawStrategy() {
        this(bound -> ThreadLocalRandom.current().nextLong(bound));
    }

    /**
     * 使用可注入的随机源创建策略，支持确定性测试。
     *
     * @param randomGenerator 返回 {@code [0, bound)} 区间值的函数
     */
    public WeightedDrawStrategy(LongUnaryOperator randomGenerator) {
        this.randomGenerator = Objects.requireNonNull(randomGenerator, "randomGenerator must not be null");
    }

    @Override
    public Optional<DrawCandidate> draw(List<DrawCandidate> candidates, int noWinWeight) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        if (noWinWeight < 0) {
            throw new IllegalArgumentException("No-win weight must not be negative");
        }

        List<DrawCandidate> eligibleCandidates = new ArrayList<>();
        long totalPrizeWeight = 0L;

        for (DrawCandidate candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate must not be null");
            if (candidate.weight() < 0) {
                throw new IllegalArgumentException(
                    "Candidate weight must not be negative"
                );
            }
            if (candidate.remainingInventory() < 0) {
                throw new IllegalArgumentException(
                    "Candidate remaining inventory must not be negative"
                );
            }
            if (candidate.remainingInventory() == 0 || candidate.weight() == 0) {
                continue;
            }

            eligibleCandidates.add(candidate);
            totalPrizeWeight = Math.addExact(totalPrizeWeight, candidate.weight());
        }

        // 没有候选奖品时返回未中奖结果
        if (eligibleCandidates.isEmpty()) {
            return Optional.empty();
        }

        long totalWeight = Math.addExact(totalPrizeWeight, noWinWeight);
        long selectedWeight = randomGenerator.applyAsLong(totalWeight);
        long cumulativeWeight = 0L;
        // 每个候选奖品占用区间：[上一个累计权重, 新累计权重)。
        for (DrawCandidate candidate : eligibleCandidates) {
            cumulativeWeight += candidate.weight();
            if (selectedWeight < cumulativeWeight) {
                return Optional.of(candidate);
            }
        }

        // 未落入任何奖品区间的值位于末尾的未中奖区间。
        return Optional.empty();
    }
}
