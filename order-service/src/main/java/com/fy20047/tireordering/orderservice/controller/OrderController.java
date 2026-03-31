package com.fy20047.tireordering.orderservice.controller;

import com.fy20047.tireordering.orderservice.dto.CreateOrderRequest;
import com.fy20047.tireordering.orderservice.dto.CreateOrderResponse;
import com.fy20047.tireordering.orderservice.entity.Order;
import com.fy20047.tireordering.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 這個檔案用途：
// 提供前台建單 API，負責接收請求、欄位驗證、轉呼叫訂單服務並回傳建立結果。
@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    // 這段欄位用途：呼叫訂單領域服務執行建單。
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 這段方法用途：建立新訂單，成功時回傳 201 Created。
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
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
