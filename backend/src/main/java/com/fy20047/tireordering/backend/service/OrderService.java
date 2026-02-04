package com.fy20047.tireordering.backend.service;

import com.fy20047.tireordering.backend.entity.Order;
import com.fy20047.tireordering.backend.entity.Tire;
import com.fy20047.tireordering.backend.enums.InstallationOption;
import com.fy20047.tireordering.backend.enums.OrderStatus;
import com.fy20047.tireordering.backend.repository.OrderRepository;
import com.fy20047.tireordering.backend.repository.TireRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository; // 存訂單資訊
    private final TireRepository tireRepository; // 查輪胎資訊

    public OrderService(OrderRepository orderRepository, TireRepository tireRepository) {
        this.orderRepository = orderRepository;
        this.tireRepository = tireRepository;
    }

    // 收單
    public Order createOrder(CreateOrderCommand command) {
        // 驗證：檢查 tireId/quantity/phone/deliveryAddress 等業務規則
        validate(command);

        // 確認輪胎是否存在
        Tire tire = tireRepository.findById(command.tireId())
                .orElseThrow(() -> new IllegalArgumentException("Tire not found"));

        // 上架檢查
        if (!tire.isActive()) {
            throw new IllegalStateException("Tire is not available");
        }

        // 清洗資料
        Order order = Order.builder()
                .tire(tire)
                .quantity(command.quantity())
                .customerName(normalize(command.customerName()))
                .phone(normalize(command.phone()))
                .email(normalize(command.email()))
                .installationOption(command.installationOption())
                .deliveryAddress(normalize(command.deliveryAddress()))
                .carModel(normalize(command.carModel()))
                .notes(normalize(command.notes()))
                .build();

        // 成立訂單，存到 DB
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    // 後台查詢訂單列表
    public List<Order> listOrders(OrderStatus status) {
        if (status == null) {
            return orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    // 更新狀態
    public Order updateOrderStatus(Long id, OrderStatus status) {
        Order order = getOrderById(id); // 取得訂單（共用）
        order.setStatus(status);
        return orderRepository.save(order);
    }

    private void validate(CreateOrderCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Order payload is required");
        }
        if (command.tireId() == null) {
            throw new IllegalArgumentException("tireId is required");
        }
        if (command.quantity() == null || command.quantity() < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        if (isBlank(command.customerName())) {
            throw new IllegalArgumentException("customerName is required");
        }
        if (isBlank(command.phone())) {
            throw new IllegalArgumentException("phone is required");
        }
        if (isBlank(command.carModel())) {
            throw new IllegalArgumentException("carModel is required");
        }
        if (command.installationOption() == null) {
            throw new IllegalArgumentException("installationOption is required");
        }
        if (command.installationOption() == InstallationOption.DELIVERY
                && isBlank(command.deliveryAddress())) {
            throw new IllegalArgumentException("deliveryAddress is required for delivery");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // 把使用者輸入的多餘空白（空白鍵）修剪掉 (清洗資料)
    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    public record CreateOrderCommand(
            Long tireId,
            Integer quantity,
            String customerName,
            String phone,
            String email,
            InstallationOption installationOption,
            String deliveryAddress,
            String carModel,
            String notes
    ) { }
}
