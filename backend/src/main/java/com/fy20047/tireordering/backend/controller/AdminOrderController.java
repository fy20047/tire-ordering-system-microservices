package com.fy20047.tireordering.backend.controller;

import com.fy20047.tireordering.backend.dto.AdminOrderListResponse;
import com.fy20047.tireordering.backend.dto.AdminOrderResponse;
import com.fy20047.tireordering.backend.dto.UpdateOrderStatusRequest;
import com.fy20047.tireordering.backend.entity.Order;
import com.fy20047.tireordering.backend.enums.OrderStatus;
import com.fy20047.tireordering.backend.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 後台訂單管理 API（列表 + 狀態更新），把請求轉成 Service 的操作，回傳 DTO
@RestController
@RequestMapping("/api/admin/orders")
@ConditionalOnProperty(
        name = "feature.backend-order-endpoints-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public AdminOrderListResponse list(@RequestParam(required = false) OrderStatus status) {
        List<Order> orders = orderService.listOrders(status);
        List<AdminOrderResponse> items = orders.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new AdminOrderListResponse(items);
    }

    // 更新訂單狀態
    @PatchMapping("/{id}/status")
    public AdminOrderResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        Order updated = orderService.updateOrderStatus(id, request.status());
        return toResponse(updated);
    }

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
                order.getTire().getId(),
                order.getTire().getBrand(),
                order.getTire().getSeries(),
                order.getTire().getOrigin(),
                order.getTire().getSize(),
                order.getTire().getPrice()
        );
    }
}
