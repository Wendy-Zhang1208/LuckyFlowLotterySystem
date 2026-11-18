package com.luckyflow.lottery.model;

/**
 * 抽奖请求登记结果。
 *
 * <p>新请求通过 {@code executionToken} 获得本轮执行权；已经成功的请求直接携带原结果，
 * 调用方不得再次预扣 Redis 或重复写入业务记录。</p>
 */
public record DrawRequestClaim(
        String executionToken,
        DrawResult completedResult
) {

    public static DrawRequestClaim claimed(String executionToken) {
        return new DrawRequestClaim(executionToken, null);
    }

    public static DrawRequestClaim completed(DrawResult completedResult) {
        return new DrawRequestClaim(null, completedResult);
    }

    public boolean alreadyCompleted() {
        return completedResult != null;
    }
}
