package com.fy20047.tireordering.authservice.security;

import com.fy20047.tireordering.authservice.config.JwtProperties;
import com.fy20047.tireordering.authservice.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.springframework.stereotype.Service;

// 這個檔案用途：
// 集中處理 Access Token 的簽發與驗證，並統一封裝 RS256 金鑰解析細節。
@Service
public class JwtService {

    // 這段欄位用途：RS256 私鑰/公鑰與 token 有效時間設定。
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long expirationSeconds;

    public JwtService(JwtProperties properties) {
        this.privateKey = parsePrivateKey(properties.privateKey());
        this.publicKey = parsePublicKey(properties.publicKey());
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
                .signWith(privateKey)
                .compact();
    }

    // 這段方法用途：解析與驗證 token（供後續需要驗章的流程重用）。
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 這段方法用途：把 PEM 格式私鑰字串轉為 Java PrivateKey。
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

    // 這段方法用途：把 PEM 格式公鑰字串轉為 Java PublicKey。
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

    // 這段方法用途：移除 PEM 標頭/尾與空白，留下可 Base64 decode 的純內容。
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
