package com.fy20047.tireordering.tireservice.security;

import com.fy20047.tireordering.tireservice.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Service;

// 這個檔案用途：
// 封裝 Tire Service 的 JWT 驗章流程（RS256 public key），供授權過濾器重用。
@Service
public class JwtService {

    // 這段欄位用途：保存解析後的 RSA 公鑰，避免每次請求重複解析 PEM。
    private final PublicKey publicKey;

    public JwtService(JwtProperties properties) {
        this.publicKey = parsePublicKey(properties.publicKey());
    }

    // 這段方法用途：驗證 token 並回傳 claims；驗證失敗時由上層接住例外處理。
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 這段方法用途：將 PEM 格式公鑰字串轉為 Java PublicKey 物件。
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

    // 這段方法用途：移除 PEM 標頭/尾與空白，留下可解碼內容。
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
