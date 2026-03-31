package com.fy20047.tireordering.authservice.entity;

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
// 映射 admins 資料表，保存管理員帳號與密碼雜湊，供登入驗證與 refresh token 關聯使用。
@Entity
@Table(name = "admins")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {

    // 這段欄位用途：管理員主鍵 ID。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 這段欄位用途：管理員登入帳號（唯一）。
    @Column(nullable = false, unique = true, length = 100)
    private String username;

    // 這段欄位用途：密碼雜湊值（不存明文密碼）。
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // 這段欄位用途：建立時間。
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 這段欄位用途：最後更新時間。
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 這段生命週期用途：初次入庫時自動補建立/更新時間。
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 這段生命週期用途：每次更新時自動刷新 updatedAt。
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
