package com.luckyflow.lottery.constant;

public class OutboxEventStatusConstants {
    private OutboxEventStatusConstants() {
    }

    public static final int PENDING = 0;

    public static final int PROCESSING = 1;

    public static final int PUBLISHED = 2;
}
