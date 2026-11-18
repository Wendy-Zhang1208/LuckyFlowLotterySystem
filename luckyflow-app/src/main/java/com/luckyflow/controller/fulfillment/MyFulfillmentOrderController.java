package com.luckyflow.controller.fulfillment;

import com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.ClaimRequest;
import com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.UserOrderDetail;
import com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.UserOrderSummary;
import com.luckyflow.fulfillment.entity.FulfillmentOrder;
import com.luckyflow.fulfillment.service.FulfillmentService;
import com.luckyflow.fulfillment.service.FulfillmentService.FulfillmentOrderPage;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import com.luckyflow.security.CurrentUserAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.toUserDetail;
import static com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.toUserSummary;

/** 用户履约接口只从可信安全上下文取得 userId。 */
@RestController
@RequestMapping("/api/me/fulfillment-orders")
public class MyFulfillmentOrderController {

    private final FulfillmentService fulfillmentService;
    private final CurrentUserAccessor currentUserAccessor;

    public MyFulfillmentOrderController(
            FulfillmentService fulfillmentService,
            CurrentUserAccessor currentUserAccessor
    ) {
        this.fulfillmentService = fulfillmentService;
        this.currentUserAccessor = currentUserAccessor;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserOrderSummary>> findMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        long userId = currentUserAccessor.requireUserId();
        FulfillmentOrderPage result = fulfillmentService.findMyOrders(userId, page, size, status);
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(order -> toUserSummary(order)).toList(),
                result.page(),
                result.size(),
                result.total()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserOrderDetail> findMyOrder(@PathVariable long id) {
        long userId = currentUserAccessor.requireUserId();
        return ApiResponse.success(toUserDetail(fulfillmentService.findMyOrder(userId, id)));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<UserOrderDetail> claimMyOrder(
            @PathVariable long id,
            @RequestBody(required = false) ClaimRequest request
    ) {
        long userId = currentUserAccessor.requireUserId();
        FulfillmentOrder updated = fulfillmentService.claimMyOrder(
                userId,
                id,
                request == null ? null : request.toCommand()
        );
        return ApiResponse.success(toUserDetail(updated));
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<UserOrderDetail> receiveMyOrder(@PathVariable long id) {
        long userId = currentUserAccessor.requireUserId();
        return ApiResponse.success(toUserDetail(fulfillmentService.receiveMyOrder(userId, id)));
    }
}
