package com.luckyflow.lottery.service.Interface;

/** 依据持久化请求与资源意图恢复未完成的 Redis 补偿。 */
public interface DrawRequestRecoveryService {

    void handleTransactionCompletion(String requestId, String executionToken, int completionStatus);

    void cancelAndRecover(String requestId, String executionToken);

    int recoverBatch(int batchSize);
}
