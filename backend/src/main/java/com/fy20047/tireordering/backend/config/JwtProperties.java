package com.fy20047.tireordering.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 讀取 JWT 設定（secret、過期時間）及 refresh 相關設定欄位
// 把 secret / expirationSeconds 集中管理
// secret 必須至少 32 字元
// 用環境變數注入
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        long expirationSeconds,
        long refreshExpirationSeconds,
        String refreshCookieName,
        boolean refreshCookieSecure,
        String refreshCookieSameSite
) {
}
