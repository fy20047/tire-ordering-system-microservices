package com.fy20047.tireordering.tireservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 這個檔案用途：
// 將 JWT 相關設定綁定為型別安全物件，供安全層讀取 RS256 金鑰與 token 參數。
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String privateKey,
        String publicKey,
        long expirationSeconds
) {
}
