package com.fy20047.tireordering.orderservice.repository;

import com.fy20047.tireordering.orderservice.entity.Order;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// 這個檔案用途：
// 提供訂單資料存取介面，供 OrderService 查詢與更新訂單使用。
public interface OrderRepository extends JpaRepository<Order, Long> {

    // 這段方法用途：取得全部訂單，依建立時間新到舊排序。
    List<Order> findAllByOrderByCreatedAtDesc();

    // 這段方法用途：依狀態篩選訂單，依建立時間新到舊排序。
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
