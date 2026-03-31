package com.fy20047.tireordering.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 這個檔案用途：
// 綁定 application.yaml 的 security.jwt.* 設定，讓 JWT 與 refresh cookie 參數集中管理。
// 後續切換 RS256 時，會在這裡延伸金鑰相關欄位，避免分散在多個類別。
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        // Access Token 參數：目前先沿用既有 secret，後續改 RS256 時會替換簽章方式。
        String secret,
        long expirationSeconds,
        // Refresh Token 與 Cookie 行為參數。
        long refreshExpirationSeconds,
        String refreshCookieName,
        boolean refreshCookieSecure,
        String refreshCookieSameSite
) {
}
