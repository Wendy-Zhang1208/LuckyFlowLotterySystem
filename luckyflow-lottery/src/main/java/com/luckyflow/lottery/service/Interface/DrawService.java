package com.luckyflow.lottery.service.Interface;

import com.luckyflow.lottery.model.DrawResult;

/**
 * 执行完整活动抽奖流程的应用服务接口。
 */
public interface DrawService {

    /**
     * 为指定用户和活动执行一次抽奖。
     *
     * @param userId 参与抽奖的用户标识
     * @param activityId 目标活动标识
     * @return 已提交的抽奖结果
     */
    /**
     * 使用调用方稳定提供的 requestId 执行抽奖；网络重试必须复用同一个 requestId。
     */
    DrawResult draw(String requestId, Long userId, Long activityId);
}
