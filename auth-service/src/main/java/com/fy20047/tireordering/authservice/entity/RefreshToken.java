package com.fy20047.tireordering.authservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 這個檔案用途：
// 映射 admin_refresh_tokens 資料表，存 refresh token 的 hash 與生命週期狀態（過期/撤銷）。
@Entity
@Table(name = "admin_refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    // 這段欄位用途：refresh token 主鍵 ID。
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 這段欄位用途：關聯管理員（多個 token 可對應同一 admin）。
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    // 這段欄位用途：refresh token 的 SHA-256 hash（只存 hash，不存原文）。
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    // 這段欄位用途：token 到期時間。
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 這段欄位用途：token 撤銷時間（null 代表未撤銷）。
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // 這段欄位用途：建立時間。
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 這段生命週期用途：初次入庫時自動補 createdAt。
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 這段方法用途：判斷 token 是否已被撤銷。
    public boolean isRevoked() {
        return revokedAt != null;
    }

    // 這段方法用途：判斷 token 是否已過期。
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
