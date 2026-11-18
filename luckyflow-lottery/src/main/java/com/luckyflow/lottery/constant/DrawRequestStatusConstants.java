package com.luckyflow.lottery.constant;

/**
 * 抽奖请求的持久化状态。
 *
 * <p>INIT 表示请求仍可能需要恢复；SUCCESS 和 CANCELLED 均为终态。</p>
 */
public final class DrawRequestStatusConstants {

    public static final int INIT = 0;

    public static final int SUCCESS = 1;

    public static final int CANCELLED = 2;

    private DrawRequestStatusConstants() {
    }
}
