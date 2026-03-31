package com.fy20047.tireordering.orderservice.service;

import com.fy20047.tireordering.orderservice.entity.Order;
import com.fy20047.tireordering.orderservice.entity.Tire;
import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import com.fy20047.tireordering.orderservice.repository.OrderRepository;
import com.fy20047.tireordering.orderservice.repository.TireRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 這個檔案用途：
// 封裝訂單領域核心流程（建單、查單、狀態更新）與基礎商業規則驗證。
@Service
@Transactional
public class OrderService {

    // 這段欄位用途：訂單資料儲存與輪胎主檔查詢（過渡期）。
    private final OrderRepository orderRepository;
    private final TireRepository tireRepository;

    public OrderService(OrderRepository orderRepository, TireRepository tireRepository) {
        this.orderRepository = orderRepository;
        this.tireRepository = tireRepository;
    }

    // 這段方法用途：建立新訂單，包含輸入驗證與商品可下單檢查。
    public Order createOrder(CreateOrderCommand command) {
        validate(command);

        Tire tire = tireRepository.findById(command.tireId())
                .orElseThrow(() -> new IllegalArgumentException("Tire not found"));

        if (!tire.isActive()) {
            throw new IllegalStateException("Tire is not available");
        }

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

        return orderRepository.save(order);
    }

    // 這段方法用途：後台查詢訂單列表，可選擇是否依狀態過濾。
    @Transactional(readOnly = true)
    public List<Order> listOrders(OrderStatus status) {
        if (status == null) {
            return orderRepository.findAllByOrderByCreatedAtDesc();
        }
        return orderRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    // 這段方法用途：更新指定訂單狀態。
    public Order updateOrderStatus(Long id, OrderStatus status) {
        Order order = getOrderById(id);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    // 這段方法用途：依訂單編號查詢單筆資料，不存在時丟出例外。
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
    }

    // 這段方法用途：集中處理建單輸入資料驗證規則。
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

    // 這段方法用途：判斷字串是否為空白（null 或去空白後為空）。
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // 這段方法用途：清洗字串輸入，多餘空白轉為 null 或去除首尾空白。
    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    // 這個 record 用途：
    // 封裝建單時服務層所需欄位，讓 Controller 與 Service 解耦。
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
    ) {
    }
}
