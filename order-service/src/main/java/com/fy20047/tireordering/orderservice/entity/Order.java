package com.fy20047.tireordering.orderservice.entity;

import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 這個檔案用途：
// Order 領域核心資料模型，對應既有 tire_orders 資料表，先保持相容欄位供 Phase 4 漸進搬移。
@Entity
@Table(name = "tire_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    // 這段欄位用途：訂單主鍵。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 這段欄位用途：過渡期先沿用對輪胎主檔的關聯（後續 Snapshot 步驟會移除）。
    @ManyToOne(optional = false)
    @JoinColumn(name = "tire_id", nullable = false)
    private Tire tire;

    // 這段欄位用途：訂單購買數量。
    @Column(nullable = false)
    private Integer quantity;

    // 這段欄位用途：顧客聯絡資訊。
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(length = 255)
    private String email;

    // 這段欄位用途：交付方式與配送地址。
    @Enumerated(EnumType.STRING)
    @Column(name = "installation_option", nullable = false, length = 20)
    private InstallationOption installationOption;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    // 這段欄位用途：訂單狀態、車型與備註。
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "car_model", length = 100)
    private String carModel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // 這段欄位用途：建立/更新時間戳記。
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 這段生命週期方法用途：建立時補齊時間欄位。
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 這段生命週期方法用途：更新時刷新 updatedAt。
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
