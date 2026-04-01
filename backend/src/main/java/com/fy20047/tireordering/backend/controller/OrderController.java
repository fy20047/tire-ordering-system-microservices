package com.fy20047.tireordering.backend.controller;

import com.fy20047.tireordering.backend.dto.CreateOrderRequest;
import com.fy20047.tireordering.backend.dto.CreateOrderResponse;
import com.fy20047.tireordering.backend.entity.Order;
import com.fy20047.tireordering.backend.service.OrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@Validated
@ConditionalOnProperty(
        name = "feature.backend-order-endpoints-enabled",
        havingValue = "true",
        matchIfMissing = false
)
// 處理資料接收與驗證、輸入 DTO -> Service -> 資料庫、指定 201 Created
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 寫入/提交 請求
    // Spring 解析 JSON → 封裝成 CreateOrderRequest
    // @Valid 先跑 DTO 驗證（例如 customerName 是否空白）
    // 驗證成功 → 進 Service
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // 把 DTO (前端資料) 轉成 Command (Service 專用指令)
        OrderService.CreateOrderCommand command = new OrderService.CreateOrderCommand(
                request.tireId(),
                request.quantity(),
                request.customerName(),
                request.phone(),
                request.email(),
                request.installationOption(),
                request.deliveryAddress(),
                request.carModel(),
                request.notes()
        );

        Order order = orderService.createOrder(command);
        CreateOrderResponse response = new CreateOrderResponse(
                order.getId(),
                order.getStatus(),
                "訂單已送出，客服將與您聯繫確認。"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
