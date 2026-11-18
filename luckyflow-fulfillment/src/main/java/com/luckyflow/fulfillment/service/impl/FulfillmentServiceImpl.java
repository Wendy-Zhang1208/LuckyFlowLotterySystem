package com.luckyflow.fulfillment.service.impl;

import com.luckyflow.exception.BusinessException;
import com.luckyflow.fulfillment.entity.FulfillmentOrder;
import com.luckyflow.fulfillment.mapper.FulfillmentOrderMapper;
import com.luckyflow.fulfillment.service.FulfillmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.luckyflow.exception.ApiErrorCodes.CONFLICT;
import static com.luckyflow.exception.ApiErrorCodes.RESOURCE_NOT_FOUND;
import static com.luckyflow.exception.ApiErrorCodes.VALIDATION_FAILED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_CLAIMED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_COMPLETED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_EXPIRED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_PENDING_CLAIM;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_RECEIVED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_SHIPPED;

/**
 * 履约订单服务实现。
 *
 * <p>所有状态变更都由 Mapper 的单条条件 UPDATE 完成；随后读取仅用于返回结果或诊断失败，
 * 不能代替 UPDATE 中的属主、前置状态和领取期限条件。</p>
 */
@Service
public class FulfillmentServiceImpl implements FulfillmentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CLAIM_PERIOD_DAYS = 365;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+() -]{6,20}$");
    private static final Pattern TRACKING_NUMBER_PATTERN = Pattern.compile("^[A-Za-z0-9-]{4,100}$");

    private final FulfillmentOrderMapper fulfillmentOrderMapper;
    private final Clock clock;
    private final long claimPeriodDays;

    public FulfillmentServiceImpl(
            FulfillmentOrderMapper fulfillmentOrderMapper,
            Clock clock,
            @Value("${luckyflow.fulfillment.claim-period-days:7}") long claimPeriodDays
    ) {
        this.fulfillmentOrderMapper = Objects.requireNonNull(
                fulfillmentOrderMapper,
                "fulfillmentOrderMapper must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (claimPeriodDays < 1 || claimPeriodDays > MAX_CLAIM_PERIOD_DAYS) {
            throw new IllegalArgumentException(
                    "luckyflow.fulfillment.claim-period-days must be between 1 and 365"
            );
        }
        this.claimPeriodDays = claimPeriodDays;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializePhysicalPrizeOrder(Long winningRecordId, Long userId, LocalDateTime now) {
        requirePositive(winningRecordId, "winningRecordId");
        requirePositive(userId, "userId");
        Objects.requireNonNull(now, "now must not be null");

        FulfillmentOrder order = new FulfillmentOrder();
        order.setWinningRecordId(winningRecordId);
        order.setUserId(userId);
        order.setStatus(FULFILLMENT_STATUS_PENDING_CLAIM);
        order.setExpireTime(now.plusDays(claimPeriodDays));
        order.setCreateTime(now);
        order.setUpdateTime(now);

        try {
            // 必须加入调用方已有事务，避免消费记录与履约订单出现一边提交、一边回滚。
            int affectedRows = fulfillmentOrderMapper.insert(order);
            if (affectedRows != 1) {
                throw new IllegalStateException("Failed to initialize physical prize fulfillment order");
            }
            if (order.getId() == null) {
                throw new IllegalStateException("Fulfillment order ID was not generated");
            }
        } catch (DuplicateKeyException duplicateOrder) {
            // 唯一键竞争是正常幂等分支；锁定读确保 RR 隔离级别下读取最新已提交订单进行事实核对。
            FulfillmentOrder existingOrder =
                    fulfillmentOrderMapper.selectByWinningRecordIdForShare(winningRecordId);
            validateExistingOrder(existingOrder, winningRecordId, userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentOrderPage findMyOrders(long userId, int page, int size, String status) {
        requirePositive(userId, "userId");
        validatePage(page, size);
        Integer statusCode = parseStatus(status);
        long total = fulfillmentOrderMapper.countByUser(userId, statusCode);
        long offset = offset(page, size);
        List<FulfillmentOrder> records = offset >= total
                ? List.of()
                : fulfillmentOrderMapper.selectPageByUser(userId, statusCode, offset, size);
        return new FulfillmentOrderPage(records, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentOrder findMyOrder(long userId, long orderId) {
        requirePositive(userId, "userId");
        requirePositive(orderId, "orderId");
        return requireOwnedOrder(userId, orderId);
    }

    @Override
    @Transactional
    public FulfillmentOrder claimMyOrder(
            long userId,
            long orderId,
            ShippingAddress shippingAddress
    ) {
        requirePositive(userId, "userId");
        requirePositive(orderId, "orderId");
        ShippingAddress normalizedAddress = validateAndNormalize(shippingAddress);
        LocalDateTime now = LocalDateTime.now(clock);

        int affectedRows = fulfillmentOrderMapper.claimPendingOrder(
                orderId,
                userId,
                normalizedAddress.consigneeName(),
                normalizedAddress.consigneePhone(),
                normalizedAddress.province(),
                normalizedAddress.city(),
                normalizedAddress.district(),
                normalizedAddress.detailAddress(),
                now
        );
        if (affectedRows != 1) {
            diagnoseClaimFailure(userId, orderId, now);
        }
        return requireUpdatedOwnedOrder(userId, orderId);
    }

    @Override
    @Transactional
    public FulfillmentOrder receiveMyOrder(long userId, long orderId) {
        requirePositive(userId, "userId");
        requirePositive(orderId, "orderId");
        LocalDateTime now = LocalDateTime.now(clock);
        int affectedRows = fulfillmentOrderMapper.receiveShippedOrder(orderId, userId, now);
        if (affectedRows != 1) {
            FulfillmentOrder current = fulfillmentOrderMapper.selectByIdAndUser(orderId, userId);
            if (current == null) {
                throw orderNotFound();
            }
            throw invalidTransition("只有已发货的订单才可以确认收货");
        }
        return requireUpdatedOwnedOrder(userId, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentOrderPage findAdminOrders(int page, int size, String status) {
        validatePage(page, size);
        Integer statusCode = parseStatus(status);
        long total = fulfillmentOrderMapper.countForAdmin(statusCode);
        long offset = offset(page, size);
        List<FulfillmentOrder> records = offset >= total
                ? List.of()
                : fulfillmentOrderMapper.selectPageForAdmin(statusCode, offset, size);
        return new FulfillmentOrderPage(records, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentOrder findAdminOrder(long orderId) {
        requirePositive(orderId, "orderId");
        FulfillmentOrder order = fulfillmentOrderMapper.selectOrderById(orderId);
        if (order == null) {
            throw orderNotFound();
        }
        return order;
    }

    @Override
    @Transactional
    public FulfillmentOrder shipOrder(long orderId, ShippingInformation shippingInformation) {
        requirePositive(orderId, "orderId");
        ShippingInformation normalizedShipping = validateAndNormalize(shippingInformation);
        LocalDateTime now = LocalDateTime.now(clock);
        int affectedRows = fulfillmentOrderMapper.shipClaimedOrder(
                orderId,
                normalizedShipping.logisticsCompany(),
                normalizedShipping.trackingNumber(),
                now
        );
        if (affectedRows != 1) {
            FulfillmentOrder current = fulfillmentOrderMapper.selectOrderById(orderId);
            if (current == null) {
                throw orderNotFound();
            }
            throw invalidTransition("只有已领取的订单才可以发货");
        }
        FulfillmentOrder updated = fulfillmentOrderMapper.selectOrderById(orderId);
        if (updated == null) {
            throw new IllegalStateException("Fulfillment order disappeared after shipping");
        }
        return updated;
    }

    private void diagnoseClaimFailure(long userId, long orderId, LocalDateTime now) {
        FulfillmentOrder current = fulfillmentOrderMapper.selectByIdAndUser(orderId, userId);
        if (current == null) {
            // 不区分“订单不存在”和“不是本人订单”，避免通过 ID 探测其他用户订单。
            throw orderNotFound();
        }
        if (Integer.valueOf(FULFILLMENT_STATUS_PENDING_CLAIM).equals(current.getStatus())
                && current.getExpireTime() != null
                && !current.getExpireTime().isAfter(now)) {
            throw new BusinessException(CONFLICT, "该订单已超过领取截止时间");
        }
        throw invalidTransition("只有待领取且未过期的订单才可以提交收货信息");
    }

    private FulfillmentOrder requireOwnedOrder(long userId, long orderId) {
        FulfillmentOrder order = fulfillmentOrderMapper.selectByIdAndUser(orderId, userId);
        if (order == null) {
            throw orderNotFound();
        }
        return order;
    }

    private FulfillmentOrder requireUpdatedOwnedOrder(long userId, long orderId) {
        FulfillmentOrder updated = fulfillmentOrderMapper.selectByIdAndUser(orderId, userId);
        if (updated == null) {
            throw new IllegalStateException("Fulfillment order disappeared after state transition");
        }
        return updated;
    }

    private void validateExistingOrder(
            FulfillmentOrder existingOrder,
            Long winningRecordId,
            Long userId
    ) {
        if (existingOrder == null) {
            throw new IllegalStateException(
                    "Duplicate fulfillment order was reported but the existing order was not found"
            );
        }
        if (!Objects.equals(existingOrder.getWinningRecordId(), winningRecordId)
                || !Objects.equals(existingOrder.getUserId(), userId)) {
            throw new IllegalStateException(
                    "Existing fulfillment order conflicts with the current winning fact"
            );
        }
    }

    private ShippingAddress validateAndNormalize(ShippingAddress address) {
        if (address == null) {
            throw validationFailure("请填写收货信息");
        }
        String name = requiredText(address.consigneeName(), "收货人姓名", 50);
        String phone = requiredText(address.consigneePhone(), "收货人手机号", 20);
        if (!PHONE_PATTERN.matcher(phone).matches()
                || phone.chars().filter(Character::isDigit).count() < 6) {
            throw validationFailure("请填写有效的收货人手机号");
        }
        return new ShippingAddress(
                name,
                phone,
                requiredText(address.province(), "省/州", 50),
                requiredText(address.city(), "城市", 50),
                requiredText(address.district(), "区县", 50),
                requiredText(address.detailAddress(), "详细地址", 255)
        );
    }

    private ShippingInformation validateAndNormalize(ShippingInformation shippingInformation) {
        if (shippingInformation == null) {
            throw validationFailure("请填写物流信息");
        }
        String company = requiredText(
                shippingInformation.logisticsCompany(),
                "物流公司",
                100
        );
        String trackingNumber = requiredText(
                shippingInformation.trackingNumber(),
                "运单号",
                100
        );
        if (!TRACKING_NUMBER_PATTERN.matcher(trackingNumber).matches()) {
            throw validationFailure("运单号只能包含英文字母、数字和连字符，且长度为 4 至 100 位");
        }
        return new ShippingInformation(company, trackingNumber);
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw validationFailure("请填写" + fieldName);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || containsControlCharacter(normalized)) {
            throw validationFailure(fieldName + "格式不正确或长度超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private Integer parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING_CLAIM" -> FULFILLMENT_STATUS_PENDING_CLAIM;
            case "CLAIMED" -> FULFILLMENT_STATUS_CLAIMED;
            case "SHIPPED" -> FULFILLMENT_STATUS_SHIPPED;
            case "RECEIVED" -> FULFILLMENT_STATUS_RECEIVED;
            case "COMPLETED" -> FULFILLMENT_STATUS_COMPLETED;
            case "EXPIRED" -> FULFILLMENT_STATUS_EXPIRED;
            default -> throw validationFailure("不支持的履约订单状态");
        };
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw validationFailure("page 必须大于等于 1，size 必须在 1 至 100 之间");
        }
    }

    private long offset(int page, int size) {
        return Math.multiplyExact((long) page - 1L, size);
    }

    private void requirePositive(Number value, String fieldName) {
        if (value == null || value.longValue() <= 0) {
            throw validationFailure(fieldName + " must be positive");
        }
    }

    private BusinessException orderNotFound() {
        return new BusinessException(RESOURCE_NOT_FOUND, "履约订单不存在");
    }

    private BusinessException invalidTransition(String message) {
        return new BusinessException(CONFLICT, message);
    }

    private BusinessException validationFailure(String message) {
        return new BusinessException(VALIDATION_FAILED, message);
    }
}
