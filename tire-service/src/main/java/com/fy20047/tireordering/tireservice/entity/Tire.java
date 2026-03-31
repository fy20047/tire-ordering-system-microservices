package com.fy20047.tireordering.tireservice.entity;

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
// 定義 Tire Service 的輪胎資料模型，對應資料表 `tires`，供 Repository/Service 使用。
@Entity
@Table(name = "tires")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tire {

    // 這段欄位用途：主鍵與自動遞增設定。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 這段欄位用途：輪胎品牌與系列資料。
    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String series;

    // 這段欄位用途：產地資訊（可為空）。
    @Column(length = 50)
    private String origin;

    // 這段欄位用途：輪胎規格與售價資訊。
    @Column(nullable = false, length = 50)
    private String size;

    @Column
    private Integer price;

    // 這段欄位用途：上下架狀態，預設為上架（true）。
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // 這段欄位用途：建立/更新時間戳記，供管理端排序與追蹤。
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 這段方法用途：資料首次寫入前，自動補齊建立與更新時間。
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 這段方法用途：資料更新前，自動刷新更新時間。
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
