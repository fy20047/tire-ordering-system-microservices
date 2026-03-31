package com.fy20047.tireordering.backend.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// 這個測試工具檔案用途：
// 在測試期間即時產生 RSA 金鑰，避免把 private key 明文放在 repository。
public final class TestRsaKeyPairFactory {

    // 這個 record 的用途：
    // 包裝測試時要注入的 PEM 私鑰與公鑰。
    public record PemKeyPair(String privateKeyPem, String publicKeyPem) {
    }

    // 這段建構子用途：
    // 防止工具類別被建立實體。
    private TestRsaKeyPairFactory() {
    }

    // 這個方法用途：
    // 產生 2048-bit RSA 金鑰，回傳可直接用於 JwtProperties 的 PEM 格式字串。
    public static PemKeyPair generate() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            String privatePem = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
            String publicPem = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());
            return new PemKeyPair(privatePem, publicPem);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to generate RSA test key pair", ex);
        }
    }

    // 這個方法用途：
    // 將 DER 位元資料轉換為 PEM 格式，確保與正式 JWT 金鑰解析流程一致。
    private static String toPem(String keyType, byte[] derBytes) {
        Base64.Encoder base64Encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
        String base64Body = base64Encoder.encodeToString(derBytes);
        return "-----BEGIN " + keyType + "-----\n" + base64Body + "\n-----END " + keyType + "-----";
    }
}
