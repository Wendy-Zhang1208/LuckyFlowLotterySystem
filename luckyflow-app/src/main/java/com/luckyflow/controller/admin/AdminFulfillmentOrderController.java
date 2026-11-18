package com.luckyflow.controller.admin;

import com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.AdminOrderDetail;
import com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.AdminOrderSummary;
import com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.ShipRequest;
import com.luckyflow.fulfillment.entity.FulfillmentOrder;
import com.luckyflow.fulfillment.service.FulfillmentService;
import com.luckyflow.fulfillment.service.FulfillmentService.FulfillmentOrderPage;
import com.luckyflow.response.ApiResponse;
import com.luckyflow.response.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.toAdminDetail;
import static com.luckyflow.controller.fulfillment.dto.FulfillmentOrderHttpDto.toAdminSummary;

/** 管理端履约仅开放查询与单笔发货；ADMIN 鉴权由统一安全链保护 /api/admin/**。 */
@RestController
@RequestMapping("/api/admin/fulfillment-orders")
public class AdminFulfillmentOrderController {

    private final FulfillmentService fulfillmentService;

    public AdminFulfillmentOrderController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOrderSummary>> findOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        FulfillmentOrderPage result = fulfillmentService.findAdminOrders(page, size, status);
        return ApiResponse.success(new PageResponse<>(
                result.records().stream().map(order -> toAdminSummary(order)).toList(),
                result.page(),
                result.size(),
                result.total()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminOrderDetail> findOrder(@PathVariable long id) {
        return ApiResponse.success(toAdminDetail(fulfillmentService.findAdminOrder(id)));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<AdminOrderDetail> shipOrder(
            @PathVariable long id,
            @RequestBody(required = false) ShipRequest request
    ) {
        FulfillmentOrder updated = fulfillmentService.shipOrder(
                id,
                request == null ? null : request.toCommand()
        );
        return ApiResponse.success(toAdminDetail(updated));
    }
}
