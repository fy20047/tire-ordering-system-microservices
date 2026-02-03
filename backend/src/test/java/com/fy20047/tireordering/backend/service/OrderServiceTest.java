package com.fy20047.tireordering.backend.service; // 啟用 Mockito

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fy20047.tireordering.backend.entity.Order;
import com.fy20047.tireordering.backend.entity.Tire;
import com.fy20047.tireordering.backend.enums.InstallationOption;
import com.fy20047.tireordering.backend.enums.OrderStatus;
import com.fy20047.tireordering.backend.repository.OrderRepository;
import com.fy20047.tireordering.backend.repository.TireRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// 測試類別的設定 (The Setup)
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock // 建立假物件
    private OrderRepository orderRepository;

    @Mock
    private TireRepository tireRepository;

    @InjectMocks // 注入假物件
    private OrderService orderService;

    private Tire activeTire; // 測試用的共用資料

    @BeforeEach // 每個測試前執行
    void setUp() { // 初始化 activeTire
        activeTire = Tire.builder()
                .brand("Brand")
                .series("Series")
                .size("205/55R16")
                .isActive(true)
                .build();
        activeTire.setId(1L);
    }

    // 測試異常狀況 (Unhappy Paths)
    @Test // 標示這是測試案例
    void createOrder_whenCommandNull_shouldThrow() {
        // Assertion 會拋出例外
        IllegalArgumentException ex = assertThrows( // assertThrows (設陷阱)：預期這段程式碼一定會爆掉，而且要是指定的錯誤類型
                IllegalArgumentException.class, // 預期會遇到這問題
                () -> orderService.createOrder(null) // 故意傳錯誤的 null 進去 (執行動作)
        );
        // 驗證錯誤訊息，檢查 ex.getMessage()，確認它寫的是 "Order payload is required"，而不是其他奇怪的錯誤
        assertEquals("Order payload is required", ex.getMessage()); // assertThrows 成功接住了這個例外，把它存到 ex 變數裡
    }

    // 測試外部依賴回傳特定結果 (Mocking Behavior)
    @Test
    void createOrder_whenTireNotFound_shouldThrow() {
        // 設定 Mock 行為 (Stubbing)
        when(tireRepository.findById(1L)).thenReturn(Optional.empty());

        OrderService.CreateOrderCommand command = new OrderService.CreateOrderCommand(
                1L,
                2,
                "Alice",
                "0912",
                "test@example.com",
                InstallationOption.INSTALL,
                null,
                "Altis",
                null
        );

        // 執行與驗證
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(command)
        );
        assertEquals("Tire not found", ex.getMessage()); // OrderService.java 第 32 行
    }

    @Test
    void createOrder_whenTireInactive_shouldThrow() {
        Tire inactive = Tire.builder()
                .brand("Brand")
                .series("Series")
                .size("205/55R16")
                .isActive(false)
                .build();
        inactive.setId(1L);

        when(tireRepository.findById(1L)).thenReturn(Optional.of(inactive));

        OrderService.CreateOrderCommand command = new OrderService.CreateOrderCommand(
                1L,
                2,
                "Alice",
                "0912",
                "test@example.com",
                InstallationOption.INSTALL,
                null,
                "Altis",
                null
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrder(command)
        );
        assertEquals("Tire is not available", ex.getMessage());
    }

    @Test
    void createOrder_whenDeliveryMissingAddress_shouldThrow() {
        OrderService.CreateOrderCommand command = new OrderService.CreateOrderCommand(
                1L,
                2,
                "Alice",
                "0912",
                "test@example.com",
                InstallationOption.DELIVERY,
                "  ",
                "Altis",
                null
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(command)
        );
        assertEquals("deliveryAddress is required for delivery", ex.getMessage());
    }

    // 測試複雜邏輯與資料流 (Happy Path & ArgumentCaptor)
    @Test
    void createOrder_success_shouldNormalizeAndSave() {
        // 設定 Mock 行為：模擬存檔成功，並回傳帶有 ID 的訂單
        when(tireRepository.findById(1L)).thenReturn(Optional.of(activeTire));
        // any(Order.class) -> 不管傳進什麼 Order 物件都接受
        // 在設定 when 的時候，真正的 Order 物件還沒產生，無法指定具體的物件，只好說只要是 Order 類型的都算數
        // 在真實的資料庫裡，當 save(order) 時，資料庫會自動產生 ID ，但在測試裡 Mock Repository 是假的，它沒有資料庫功能，不會自動產生 ID
        // 所以要用 thenAnswer 來模擬存檔並產生 ID 的過程
        // Why? 因為 Service 後面可能會用到 order.getId() 來回傳給 Controller。如果我們不這樣模擬，Service 拿到的 ID 就會是 null，測試可能會失敗
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0); // 拿出來 -> Service 呼叫 save(order) 時傳進來的那個 order 物件（此時 ID 是 null），把它攔截下來
            order.setId(99L); // 貼標籤 -> 假裝自己是資料庫，幫這個訂單貼上 ID = 99
            return order; // 放回去 -> 把這個貼好 ID 的訂單回傳給 Service
        });

        // 準備髒資料（前後有空白）
        OrderService.CreateOrderCommand command = new OrderService.CreateOrderCommand(
                1L,
                2,
                "  Alice  ",
                " 0912 ",
                "   ",
                InstallationOption.INSTALL,
                "   ",
                " Altis ",
                "  notes  "
        );

        // 執行測試
        Order result = orderService.createOrder(command);
        assertEquals(99L, result.getId());

        // 抓取參數 (Capturing Arguments)，當 Service 呼叫 save 時，ArgumentCaptor 會把 Service 傳給 Repo 的那個 Order 物件抓出來
        // 這是驗證 normalize (去空白) 邏輯的唯一方法，透過 Captor 把那個在 Service 裡產生、準備存進 DB 的物件抓出來檢查，確認它的 customerName 真的被修剪乾淨
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        // 驗證資料是否被清洗 (Trim)
        assertEquals(activeTire, saved.getTire());
        assertEquals(2, saved.getQuantity());
        assertEquals("Alice", saved.getCustomerName());
        assertEquals("0912", saved.getPhone());
        assertNull(saved.getEmail());
        assertEquals(InstallationOption.INSTALL, saved.getInstallationOption());
        assertNull(saved.getDeliveryAddress());
        assertEquals("Altis", saved.getCarModel());
        assertEquals("notes", saved.getNotes());
        assertEquals(OrderStatus.PENDING, saved.getStatus());
    }

    // 測試流程控制 (verify & never)
    @Test
    void listOrders_whenStatusNull_shouldUseFindAll() {
        List<Order> orders = List.of();
        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(orders);

        // result = 執行動作
        List<Order> result = orderService.listOrders(null);

        assertEquals(orders, result);
        // 查勤
        // verify(mockObject).method() -> 事後檢查，確認 Service 剛才執行時，真的有去呼叫 Repository 的某個方法
        // 用於驗證 if (status == null) 邏輯是對的，如果 Service 寫錯了（例如不管怎樣都查全部），這裡就會報錯
        verify(orderRepository).findAllByOrderByCreatedAtDesc();
        // never() 是確認某個方法絕對沒有被執行，像這邊要查全部，就不應該去呼叫「查特定狀態」的 SQL
        verify(orderRepository, never()).findByStatusOrderByCreatedAtDesc(any(OrderStatus.class));
    }

    @Test
    void listOrders_whenStatusProvided_shouldUseFindByStatus() {
        List<Order> orders = List.of();
        when(orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING)).thenReturn(orders);

        List<Order> result = orderService.listOrders(OrderStatus.PENDING);

        assertEquals(orders, result);
        verify(orderRepository).findByStatusOrderByCreatedAtDesc(OrderStatus.PENDING);
        verify(orderRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    // 更新不存在的訂單，應該要被擋下來並報錯
    // 情境：有人想把 ID=10 的訂單改成「完成 (COMPLETED)」，但資料庫裡根本沒有這筆單
    @Test
    void updateOrderStatus_whenOrderMissing_shouldThrow() {
        // 告訴假的資料庫 (orderRepository)：「等等如果有人來查 ID=10 的訂單，你就說找不到 (Optional.empty())。」
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        // 呼叫 updateOrderStatus(10L, ...)，並預期它一定會拋出 IllegalArgumentException
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(10L, OrderStatus.COMPLETED)
        );
        // 檢查拋出來的錯誤訊息，確認它寫的是 "Order not found"
        assertEquals("Order not found", ex.getMessage());
    }

    // 正常更新訂單，應該要修改狀態並存檔
    // 情境：有一筆 ID=10 的訂單，原本是「待處理 (PENDING)」，現在要改成「完成 (COMPLETED)」
    @Test
    void updateOrderStatus_success_shouldUpdateAndSave() {
        // 先手動 new 一個訂單物件 (order)，設定 ID=10、狀態=PENDING。
        Order order = Order.builder()
                .status(OrderStatus.PENDING)
                .build();
        order.setId(10L);

        // 告訴資料庫若有人查 ID=10，你就把剛剛那個 order 交給他。
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        // 告訴資料庫若有人叫你存檔 (save)，你就假裝存好了，把東西還給他
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 呼叫 updateOrderStatus(10L, COMPLETED)
        Order result = orderService.updateOrderStatus(10L, OrderStatus.COMPLETED);

        // 檢查拿回來的訂單，狀態是不是真的變成了 COMPLETED
        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        // 檢查資料庫的通聯記錄，確認 Service 真的有呼叫 save() 把修改後的訂單存回去，如果 Service 只改了狀態卻沒存檔，這行就會報錯
        verify(orderRepository).save(order);
        // 確認回傳回來的物件就是原本那個物件（同一記憶體位址）
        assertTrue(result == order);
    }
}
