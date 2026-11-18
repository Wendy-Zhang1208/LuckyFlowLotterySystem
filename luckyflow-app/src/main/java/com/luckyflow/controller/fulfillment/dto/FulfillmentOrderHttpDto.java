package com.luckyflow.controller.fulfillment.dto;

import com.luckyflow.fulfillment.entity.FulfillmentOrder;
import com.luckyflow.fulfillment.service.FulfillmentService.ShippingAddress;
import com.luckyflow.fulfillment.service.FulfillmentService.ShippingInformation;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_CLAIMED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_COMPLETED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_EXPIRED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_PENDING_CLAIM;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_RECEIVED;
import static com.luckyflow.fulfillment.constant.FulfillmentOrderStatusConstants.FULFILLMENT_STATUS_SHIPPED;

/**
 * 履约 HTTP DTO 及安全映射。
 *
 * <p>领域实体保留 Long 主键；只有这里将其转换为字符串，并集中保证列表和运营详情不会
 * 暴露完整收件信息。</p>
 */
public final class FulfillmentOrderHttpDto {

    private FulfillmentOrderHttpDto() {
    }

    public record ClaimRequest(
            String consigneeName,
            String consigneePhone,
            String province,
            String city,
            String district,
            String detailAddress
    ) {
        public ShippingAddress toCommand() {
            return new ShippingAddress(
                    consigneeName,
                    consigneePhone,
                    province,
                    city,
                    district,
                    detailAddress
            );
        }
    }

    public record ShipRequest(String logisticsCompany, String trackingNumber) {
        public ShippingInformation toCommand() {
            return new ShippingInformation(logisticsCompany, trackingNumber);
        }
    }

    public record UserOrderSummary(
            String id,
            String winningRecordId,
            String status,
            String consigneeNameMasked,
            String consigneePhoneMasked,
            String addressMasked,
            String logisticsCompany,
            String trackingNumber,
            LocalDateTime expireTime,
            LocalDateTime deliveryTime,
            LocalDateTime receiveTime,
            LocalDateTime completeTime,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public record AdminOrderSummary(
            String id,
            String winningRecordId,
            String userId,
            String status,
            String consigneeNameMasked,
            String consigneePhoneMasked,
            String addressMasked,
            String logisticsCompany,
            String trackingNumber,
            LocalDateTime expireTime,
            LocalDateTime deliveryTime,
            LocalDateTime receiveTime,
            LocalDateTime completeTime,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public record RecipientAddress(
            String consigneeName,
            String consigneePhone,
            String province,
            String city,
            String district,
            String detailAddress
    ) {
    }

    public record UserOrderDetail(
            String id,
            String winningRecordId,
            String status,
            RecipientAddress recipientAddress,
            String logisticsCompany,
            String trackingNumber,
            LocalDateTime expireTime,
            LocalDateTime deliveryTime,
            LocalDateTime receiveTime,
            LocalDateTime completeTime,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public record AdminOrderDetail(
            String id,
            String winningRecordId,
            String userId,
            String status,
            String consigneeNameMasked,
            String consigneePhoneMasked,
            String addressMasked,
            String logisticsCompany,
            String trackingNumber,
            LocalDateTime expireTime,
            LocalDateTime deliveryTime,
            LocalDateTime receiveTime,
            LocalDateTime completeTime,
            LocalDateTime createTime,
            LocalDateTime updateTime
    ) {
    }

    public static UserOrderSummary toUserSummary(FulfillmentOrder order) {
        Objects.requireNonNull(order, "order must not be null");
        return new UserOrderSummary(
                id(order.getId()),
                id(order.getWinningRecordId()),
                statusName(order.getStatus()),
                maskName(order.getConsigneeName()),
                maskPhone(order.getConsigneePhone()),
                maskAddress(order),
                order.getLogisticsCompany(),
                order.getTrackingNumber(),
                order.getExpireTime(),
                order.getDeliveryTime(),
                order.getReceiveTime(),
                order.getCompleteTime(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    public static AdminOrderSummary toAdminSummary(FulfillmentOrder order) {
        Objects.requireNonNull(order, "order must not be null");
        return new AdminOrderSummary(
                id(order.getId()),
                id(order.getWinningRecordId()),
                id(order.getUserId()),
                statusName(order.getStatus()),
                maskName(order.getConsigneeName()),
                maskPhone(order.getConsigneePhone()),
                maskAddress(order),
                order.getLogisticsCompany(),
                order.getTrackingNumber(),
                order.getExpireTime(),
                order.getDeliveryTime(),
                order.getReceiveTime(),
                order.getCompleteTime(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    public static UserOrderDetail toUserDetail(FulfillmentOrder order) {
        Objects.requireNonNull(order, "order must not be null");
        return new UserOrderDetail(
                id(order.getId()),
                id(order.getWinningRecordId()),
                statusName(order.getStatus()),
                fullAddress(order),
                order.getLogisticsCompany(),
                order.getTrackingNumber(),
                order.getExpireTime(),
                order.getDeliveryTime(),
                order.getReceiveTime(),
                order.getCompleteTime(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    public static AdminOrderDetail toAdminDetail(FulfillmentOrder order) {
        Objects.requireNonNull(order, "order must not be null");
        return new AdminOrderDetail(
                id(order.getId()),
                id(order.getWinningRecordId()),
                id(order.getUserId()),
                statusName(order.getStatus()),
                maskName(order.getConsigneeName()),
                maskPhone(order.getConsigneePhone()),
                maskAddress(order),
                order.getLogisticsCompany(),
                order.getTrackingNumber(),
                order.getExpireTime(),
                order.getDeliveryTime(),
                order.getReceiveTime(),
                order.getCompleteTime(),
                order.getCreateTime(),
                order.getUpdateTime()
        );
    }

    private static RecipientAddress fullAddress(FulfillmentOrder order) {
        if (Stream.of(
                order.getConsigneeName(),
                order.getConsigneePhone(),
                order.getProvince(),
                order.getCity(),
                order.getDistrict(),
                order.getDetailAddress()
        ).allMatch(value -> value == null || value.isBlank())) {
            return null;
        }
        return new RecipientAddress(
                order.getConsigneeName(),
                order.getConsigneePhone(),
                order.getProvince(),
                order.getCity(),
                order.getDistrict(),
                order.getDetailAddress()
        );
    }

    private static String maskAddress(FulfillmentOrder order) {
        String region = Stream.of(order.getProvince(), order.getCity(), order.getDistrict())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining());
        String detail = maskDetail(order.getDetailAddress());
        if (region.isBlank() && detail == null) {
            return null;
        }
        return detail == null ? region : region + " " + detail;
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String value = name.trim();
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints == 1) {
            return "*";
        }
        int firstEnd = value.offsetByCodePoints(0, 1);
        return value.substring(0, firstEnd) + "*".repeat(Math.min(3, codePoints - 1));
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = phone.replaceAll("\\s+", "");
        if (value.length() <= 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String maskDetail(String detailAddress) {
        if (detailAddress == null || detailAddress.isBlank()) {
            return null;
        }
        String value = detailAddress.trim();
        int visibleCodePoints = Math.min(2, value.codePointCount(0, value.length()));
        int visibleEnd = value.offsetByCodePoints(0, visibleCodePoints);
        return value.substring(0, visibleEnd) + "***";
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return "UNKNOWN";
        }
        return switch (status) {
            case FULFILLMENT_STATUS_PENDING_CLAIM -> "PENDING_CLAIM";
            case FULFILLMENT_STATUS_CLAIMED -> "CLAIMED";
            case FULFILLMENT_STATUS_SHIPPED -> "SHIPPED";
            case FULFILLMENT_STATUS_RECEIVED -> "RECEIVED";
            case FULFILLMENT_STATUS_COMPLETED -> "COMPLETED";
            case FULFILLMENT_STATUS_EXPIRED -> "EXPIRED";
            default -> "UNKNOWN";
        };
    }

    private static String id(Long value) {
        return Long.toString(Objects.requireNonNull(value, "HTTP resource ID must not be null"));
    }
}
