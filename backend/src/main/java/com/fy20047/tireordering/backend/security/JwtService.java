package com.fy20047.tireordering.backend.security;

import com.fy20047.tireordering.backend.config.JwtProperties;
import com.fy20047.tireordering.backend.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Service;

// 產生 / 驗證 JWT
// 讓 Controller / Service 不需要自己處理 token
// 任何 JWT 失敗都會丟例外
// RS256 金鑰格式錯誤會報 IllegalStateException
@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) { // 避免寫死，使用 JwtProperties 讀取設定檔與環境變數
        this.privateKey = parsePrivateKey(properties.privateKey());
        this.publicKey = parsePublicKey(properties.publicKey());
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
                .signWith(privateKey) // 防偽鋼印
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 把 PEM 私鑰字串轉成 Java PrivateKey
    private PrivateKey parsePrivateKey(String pem) {
        try {
            String base64 = stripPemHeaders(pem, "PRIVATE KEY");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid JWT private key", ex);
        }
    }

    // 把 PEM 公鑰字串轉成 Java PublicKey
    private PublicKey parsePublicKey(String pem) {
        try {
            String base64 = stripPemHeaders(pem, "PUBLIC KEY");
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid JWT public key", ex);
        }
    }

    // 去掉 PEM 頭尾與空白，留下可 Base64 decode 的內容
    private String stripPemHeaders(String pem, String type) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("JWT " + type + " is empty");
        }
        return pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }
}
