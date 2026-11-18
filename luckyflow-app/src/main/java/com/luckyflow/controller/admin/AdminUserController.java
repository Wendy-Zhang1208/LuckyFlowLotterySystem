package com.luckyflow.controller.admin;

import com.luckyflow.lottery.model.DrawHistoryEntry;
import com.luckyflow.lottery.model.WinningHistoryEntry;
import com.luckyflow.lottery.service.Interface.DrawHistoryService;
import com.luckyflow.publicapi.HttpIdParser;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import com.luckyflow.user.entity.PointAccount;
import com.luckyflow.user.entity.PointTransaction;
import com.luckyflow.user.entity.User;
import com.luckyflow.user.service.PointAccountService;
import com.luckyflow.user.service.PointTransactionService;
import com.luckyflow.user.service.UserAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

import static com.luckyflow.lottery.constant.DrawResultStatusConstants.DRAW_RESULT_WIN;

/** 管理端脱敏用户、积分账本和参与事实只读查询；不提供任意改积分能力。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserAccountService userAccountService;
    private final PointAccountService pointAccountService;
    private final PointTransactionService pointTransactionService;
    private final DrawHistoryService drawHistoryService;

    public AdminUserController(
            UserAccountService userAccountService,
            PointAccountService pointAccountService,
            PointTransactionService pointTransactionService,
            DrawHistoryService drawHistoryService
    ) {
        this.userAccountService = userAccountService;
        this.pointAccountService = pointAccountService;
        this.pointTransactionService = pointTransactionService;
        this.drawHistoryService = drawHistoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserSummary>> findUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserAccountService.UserPage result = userAccountService.findUsers(
                keyword,
                status,
                page,
                size
        );
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(UserSummary::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserDetail> findUser(@PathVariable String userId) {
        long parsedUserId = HttpIdParser.requirePositiveLong(userId, "userId");
        User user = userAccountService.getRequired(parsedUserId);
        PointAccount account = pointAccountService.findAccountByUserId(parsedUserId);
        return ApiResponse.success(UserDetail.from(user, account));
    }

    @GetMapping("/{userId}/point-transactions")
    public ApiResponse<PageResponse<PointTransactionView>> findPointTransactions(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long parsedUserId = HttpIdParser.requirePositiveLong(userId, "userId");
        userAccountService.getRequired(parsedUserId);
        PointTransactionService.TransactionPage result =
                pointTransactionService.findTransactionsByUserId(parsedUserId, page, size);
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(PointTransactionView::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/{userId}/draw-records")
    public ApiResponse<PageResponse<DrawRecordView>> findDrawRecords(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long parsedUserId = HttpIdParser.requirePositiveLong(userId, "userId");
        userAccountService.getRequired(parsedUserId);
        DrawHistoryService.DrawHistoryPage result =
                drawHistoryService.findDrawHistory(parsedUserId, page, size);
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(DrawRecordView::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    @GetMapping("/{userId}/winnings")
    public ApiResponse<PageResponse<WinningView>> findWinnings(
            @PathVariable String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        long parsedUserId = HttpIdParser.requirePositiveLong(userId, "userId");
        userAccountService.getRequired(parsedUserId);
        DrawHistoryService.WinningHistoryPage result =
                drawHistoryService.findWinningHistory(parsedUserId, page, size);
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(WinningView::from).toList(),
                page,
                size,
                result.total()
        ));
    }

    private static String id(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.length() <= 7
                ? "***"
                : phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public record UserSummary(
            String userId,
            String username,
            String nickname,
            String avatarKey,
            String emailMasked,
            String phoneMasked,
            String role,
            Integer status,
            LocalDateTime createTime
    ) {
        static UserSummary from(User user) {
            return new UserSummary(
                    id(user.getId()),
                    user.getAccount(),
                    user.getNickname(),
                    user.getAvatarKey(),
                    maskEmail(user.getEmail()),
                    maskPhone(user.getPhone()),
                    user.getRole(),
                    user.getStatus(),
                    user.getCreateTime()
            );
        }
    }

    public record UserDetail(
            String userId,
            String username,
            String nickname,
            String avatarKey,
            String emailMasked,
            String phoneMasked,
            String role,
            Integer status,
            Long pointBalance,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
        static UserDetail from(User user, PointAccount account) {
            return new UserDetail(
                    id(user.getId()),
                    user.getAccount(),
                    user.getNickname(),
                    user.getAvatarKey(),
                    maskEmail(user.getEmail()),
                    maskPhone(user.getPhone()),
                    user.getRole(),
                    user.getStatus(),
                    account == null ? null : account.getBalance(),
                    user.getCreateTime(),
                    user.getUpdateTime()
            );
        }
    }

    public record PointTransactionView(
            String id,
            String bizType,
            String bizId,
            Long changeAmount,
            Long afterBalance,
            LocalDateTime createTime
    ) {
        static PointTransactionView from(PointTransaction transaction) {
            return new PointTransactionView(
                    AdminUserController.id(transaction.getId()),
                    transaction.getBizType(),
                    AdminUserController.id(transaction.getBizId()),
                    transaction.getChangeAmount(),
                    transaction.getAfterBalance(),
                    transaction.getCreateTime()
            );
        }
    }

    public record DrawRecordView(
            String drawRecordId,
            String activityId,
            String activityName,
            boolean won,
            String prizeId,
            String prizeName,
            Integer prizeType,
            LocalDateTime drawTime
    ) {
        static DrawRecordView from(DrawHistoryEntry entry) {
            return new DrawRecordView(
                    id(entry.getDrawRecordId()),
                    id(entry.getActivityId()),
                    entry.getActivityName(),
                    Integer.valueOf(DRAW_RESULT_WIN).equals(entry.getResultStatus()),
                    id(entry.getPrizeId()),
                    entry.getPrizeName(),
                    entry.getPrizeType(),
                    entry.getDrawTime()
            );
        }
    }

    public record WinningView(
            String winningRecordId,
            String activityId,
            String activityName,
            String prizeId,
            String prizeName,
            Integer prizeType,
            LocalDateTime winTime
    ) {
        static WinningView from(WinningHistoryEntry entry) {
            return new WinningView(
                    id(entry.getWinningRecordId()),
                    id(entry.getActivityId()),
                    entry.getActivityName(),
                    id(entry.getPrizeId()),
                    entry.getPrizeName(),
                    entry.getPrizeType(),
                    entry.getWinTime()
            );
        }
    }
}
