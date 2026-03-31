package com.fy20047.tireordering.orderservice.service;

import com.fy20047.tireordering.orderservice.client.TireServiceClient;
import com.fy20047.tireordering.orderservice.entity.Order;
import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import com.fy20047.tireordering.orderservice.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 這個檔案用途：
// 封裝訂單領域核心流程（建單、查單、狀態更新）與基礎商業規則驗證，
// 並在建單時寫入輪胎 snapshot 以保留歷史正確性。
@Service
@Transactional
public class OrderService {

    // 這段欄位用途：訂單資料儲存與跨服務輪胎查詢（透過 tire-service 驗證可下單）。
    private final OrderRepository orderRepository;
    private final TireServiceClient tireServiceClient;

    public OrderService(OrderRepository orderRepository, TireServiceClient tireServiceClient) {
        this.orderRepository = orderRepository;
        this.tireServiceClient = tireServiceClient;
    }

    // 這段方法用途：建立新訂單，包含輸入驗證、商品可下單檢查與 snapshot 寫入。
    public Order createOrder(CreateOrderCommand command) {
        validate(command);

        // 這段呼叫用途：改為向 tire-service 查詢商品，解除對本地 Tire 資料表的直接依賴。
        TireServiceClient.TireProduct tire = tireServiceClient.getTireById(command.tireId());

        if (!tire.isActive()) {
            throw new IllegalStateException("Tire is not available");
        }
        // 這段檢查用途：避免寫入無效價格到 snapshot。
        if (tire.price() == null || tire.price() < 0) {
            throw new IllegalStateException("Tire price is invalid");
        }

        // 這段建模用途：將輪胎資訊拷貝成 snapshot 欄位，避免後續輪胎主檔變動影響歷史訂單。
        Order order = Order.builder()
                .tireId(tire.id())
                .tireSnapshotBrand(tire.brand())
                .tireSnapshotSeries(tire.series())
                .tireSnapshotOrigin(tire.origin())
                .tireSnapshotSize(tire.size())
                .tireSnapshotPrice(tire.price())
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
