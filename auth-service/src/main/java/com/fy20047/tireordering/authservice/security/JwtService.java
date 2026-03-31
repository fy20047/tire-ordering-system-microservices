package com.fy20047.tireordering.authservice.security;

import com.fy20047.tireordering.authservice.config.JwtProperties;
import com.fy20047.tireordering.authservice.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// 這個檔案用途：
// 集中處理 Access Token 的簽發與驗證，避免 Controller/Service 自己拼接 JWT 細節。
@Service
public class JwtService {

    // 這段欄位用途：JWT 簽章密鑰與 token 有效時間設定。
    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expirationSeconds();
    }

    // 這段方法用途：登入或刷新時簽發新的 access token。
    public String generateToken(Admin admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(admin.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .claim("role", "ADMIN")
                .signWith(secretKey)
                .compact();
    }

    // 這段方法用途：解析與驗證 token（供後續需要驗章的流程重用）。
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
