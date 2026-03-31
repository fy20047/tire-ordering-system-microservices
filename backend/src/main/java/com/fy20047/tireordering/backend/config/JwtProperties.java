package com.fy20047.tireordering.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 讀取 JWT 設定（RS256 private/public key、過期時間）及 refresh 相關設定欄位
// 把金鑰 / expirationSeconds 集中管理
// 用環境變數注入
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        long expirationSeconds,
        long refreshExpirationSeconds,
        String refreshCookieName,
        boolean refreshCookieSecure,
        String refreshCookieSameSite
) {
}
