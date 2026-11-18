package com.luckyflow.controller;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.lottery.model.DrawHistoryEntry;
import com.luckyflow.lottery.model.WinningHistoryEntry;
import com.luckyflow.lottery.service.Interface.DrawHistoryService;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import com.luckyflow.security.CurrentUserAccessor;
import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.entity.PointTransaction;
import com.luckyflow.user.service.PointAccountService;
import com.luckyflow.user.service.PointTransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.luckyflow.exception.ApiErrorCodes.RESOURCE_NOT_FOUND;
import static com.luckyflow.lottery.constant.DrawResultStatusConstants.DRAW_RESULT_WIN;

/** 当前登录用户的积分账本与已提交抽奖事实查询入口。 */
@RestController
@RequestMapping("/api/me")
public class MeHistoryController {

    private final CurrentUserAccessor currentUserAccessor;
    private final PointAccountService pointAccountService;
    private final PointTransactionService pointTransactionService;
    private final DrawHistoryService drawHistoryService;

    public MeHistoryController(
            CurrentUserAccessor currentUserAccessor,
            PointAccountService pointAccountService,
            PointTransactionService pointTransactionService,
            DrawHistoryService drawHistoryService
    ) {
        this.currentUserAccessor = currentUserAccessor;
        this.pointAccountService = pointAccountService;
        this.pointTransactionService = pointTransactionService;
        this.drawHistoryService = drawHistoryService;
    }

    @GetMapping("/points")
    public ApiResponse<PointBalanceResponse> findPointBalance() {
        PointAccount account = pointAccountService.findAccountByUserId(
                currentUserAccessor.requireUserId()
        );
        if (account == null) {
            throw new BusinessException(RESOURCE_NOT_FOUND, "积分账户不存在");
        }
        return ApiResponse.success(new PointBalanceResponse(
                id(account.getId()),
                account.getBalance(),
                account.getUpdateTime()
        ));
    }

    @GetMapping("/point-transactions")
    public ApiResponse<PageResponse<PointTransactionResponse>> findPointTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PointTransactionService.TransactionPage result =
                pointTransactionService.findTransactionsByUserId(
                        currentUserAccessor.requireUserId(),
                        page,
                        size
                );
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(PointTransactionResponse::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/draw-records")
    public ApiResponse<PageResponse<DrawRecordResponse>> findDrawRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DrawHistoryService.DrawHistoryPage result = drawHistoryService.findDrawHistory(
                currentUserAccessor.requireUserId(),
                page,
                size
        );
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(DrawRecordResponse::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/winnings")
    public ApiResponse<PageResponse<WinningResponse>> findWinnings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        DrawHistoryService.WinningHistoryPage result = drawHistoryService.findWinningHistory(
                currentUserAccessor.requireUserId(),
                page,
                size
        );
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(WinningResponse::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    public record PointBalanceResponse(
            String accountId,
            Long balance,
            LocalDateTime updateTime
    ) {
    }

    public record PointTransactionResponse(
            String id,
            String bizType,
            String bizId,
            Long changeAmount,
            Long afterBalance,
            LocalDateTime createTime
    ) {
        static PointTransactionResponse from(PointTransaction transaction) {
            return new PointTransactionResponse(
                    MeHistoryController.id(transaction.getId()),
                    transaction.getBizType(),
                    MeHistoryController.id(transaction.getBizId()),
                    transaction.getChangeAmount(),
                    transaction.getAfterBalance(),
                    transaction.getCreateTime()
            );
        }
    }

    public record DrawRecordResponse(
            String drawRecordId,
            String activityId,
            String activityName,
            boolean won,
            String winningRecordId,
            String activityPrizeId,
            String prizeId,
            String prizeName,
            Integer prizeType,
            LocalDateTime drawTime
    ) {
        static DrawRecordResponse from(DrawHistoryEntry entry) {
            return new DrawRecordResponse(
                    id(entry.getDrawRecordId()),
                    id(entry.getActivityId()),
                    entry.getActivityName(),
                    Integer.valueOf(DRAW_RESULT_WIN).equals(entry.getResultStatus()),
                    id(entry.getWinningRecordId()),
                    id(entry.getActivityPrizeId()),
                    id(entry.getPrizeId()),
                    entry.getPrizeName(),
                    entry.getPrizeType(),
                    entry.getDrawTime()
            );
        }
    }

    public record WinningResponse(
            String winningRecordId,
            String drawRecordId,
            String activityId,
            String activityName,
            String activityPrizeId,
            String prizeId,
            String prizeName,
            Integer prizeType,
            LocalDateTime winTime
    ) {
        static WinningResponse from(WinningHistoryEntry entry) {
            return new WinningResponse(
                    id(entry.getWinningRecordId()),
                    id(entry.getDrawRecordId()),
                    id(entry.getActivityId()),
                    entry.getActivityName(),
                    id(entry.getActivityPrizeId()),
                    id(entry.getPrizeId()),
                    entry.getPrizeName(),
                    entry.getPrizeType(),
                    entry.getWinTime()
            );
        }
    }
}
