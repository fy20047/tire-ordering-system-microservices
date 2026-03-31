package com.fy20047.tireordering.orderservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
// Phase 4 過渡期的輪胎快照來源實體，先讓 Order 核心搬移可獨立運作；
// 後續 Snapshot 步驟會把 Order 對 Tire 的直接關聯移除。
@Entity
@Table(name = "tires")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tire {

    // 這段欄位用途：輪胎主鍵。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 這段欄位用途：品牌、系列、產地、尺寸等商品識別資訊。
    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String series;

    @Column(length = 50)
    private String origin;

    @Column(nullable = false, length = 50)
    private String size;

    // 這段欄位用途：目前售價與是否上架。
    @Column
    private Integer price;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

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
