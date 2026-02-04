package com.fy20047.tireordering.backend.security;

import com.fy20047.tireordering.backend.config.JwtProperties;
import com.fy20047.tireordering.backend.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// 產生 / 驗證 JWT
// 讓 Controller / Service 不需要自己處理 token
// 任何 JWT 失敗都會丟例外
// secret 長度不足會報 WeakKeyException
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) { // 避免寫死，使用 JwtProperties 讀取設定檔與環境變數
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = properties.expirationSeconds();
    }

    // 寫登入流程 -> 發 token
    public String generateToken(Admin admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(admin.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .claim("role", "ADMIN")
                .signWith(secretKey) // 防偽鋼印
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
