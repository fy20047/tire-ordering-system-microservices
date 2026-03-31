package com.fy20047.tireordering.orderservice.controller;

import com.fy20047.tireordering.orderservice.dto.AdminOrderListResponse;
import com.fy20047.tireordering.orderservice.dto.AdminOrderResponse;
import com.fy20047.tireordering.orderservice.dto.UpdateOrderStatusRequest;
import com.fy20047.tireordering.orderservice.entity.Order;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import com.fy20047.tireordering.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 這個檔案用途：
// 提供後台訂單管理 API（列表與狀態更新），並將實體資料轉為對外 DTO。
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    // 這段欄位用途：呼叫訂單領域服務執行查詢與狀態更新。
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 這段方法用途：查詢後台訂單列表，可選擇帶狀態條件。
    @GetMapping
    public AdminOrderListResponse list(@RequestParam(required = false) OrderStatus status) {
        List<Order> orders = orderService.listOrders(status);
        List<AdminOrderResponse> items = orders.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new AdminOrderListResponse(items);
    }

    // 這段方法用途：更新指定訂單狀態。
    @PatchMapping("/{id}/status")
    public AdminOrderResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        Order updated = orderService.updateOrderStatus(id, request.status());
        return toResponse(updated);
    }

    // 這段方法用途：將訂單實體轉為後台回應 DTO（改為使用訂單內的 snapshot 欄位）。
    private AdminOrderResponse toResponse(Order order) {
        return new AdminOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getQuantity(),
                order.getCustomerName(),
                order.getPhone(),
                order.getEmail(),
                order.getInstallationOption(),
                order.getDeliveryAddress(),
                order.getCarModel(),
                order.getNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getTireId(),
                order.getTireSnapshotBrand(),
                order.getTireSnapshotSeries(),
                order.getTireSnapshotOrigin(),
                order.getTireSnapshotSize(),
                order.getTireSnapshotPrice()
        );
    }
}
