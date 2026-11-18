package com.luckyflow.marketing.service;

import com.luckyflow.marketing.entity.Prize;

import java.util.List;

/**
 * 对外提供奖品目录读取能力。
 */
public interface PrizeService {

    record PrizePage(List<Prize> records, long total) {
        public PrizePage {
            records = records == null ? List.of() : List.copyOf(records);
        }
    }

    Prize findPrize(Long prizeId);

    Prize createPrize(Prize prize);

    PrizePage findPrizes(String keyword, Integer type, Integer status, int page, int size);

    /**
     * 锁定共享奖品定义。发布复制快照和目录编辑必须使用同一行锁。
     */
    Prize lockPrizeDefinition(Long prizeId);

    /** 已被正式发布活动引用的共享奖品不可再修改；需要新定义时创建新 Prize。 */
    void updatePrizeConfiguration(Prize prize);

    /** 删除同样遵守历史发布承诺；当前 HTTP 管理端没有暴露删除入口。 */
    void deletePrize(Long prizeId);
}
