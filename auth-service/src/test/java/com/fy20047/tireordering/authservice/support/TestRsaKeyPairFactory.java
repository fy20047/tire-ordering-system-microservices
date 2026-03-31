package com.fy20047.tireordering.authservice.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

// 這個測試工具檔案用途：
// 在測試執行時動態產生 RSA 金鑰，避免把任何 private key 明文提交到版本庫。
public final class TestRsaKeyPairFactory {

    // 這個 record 的用途：
    // 封裝測試所需的 PEM 私鑰與公鑰字串，讓各測試可直接注入設定。
    public record PemKeyPair(String privateKeyPem, String publicKeyPem) {
    }

    // 這段建構子用途：
    // 工具類別不需要被實例化，防止誤用。
    private TestRsaKeyPairFactory() {
    }

    // 這個方法用途：
    // 產生一組 2048-bit RSA 金鑰，並轉成 PEM 格式字串給測試使用。
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
    // 把 DER 位元資料轉為 PEM 文字，與正式環境 JWT 解析格式一致。
    private static String toPem(String keyType, byte[] derBytes) {
        Base64.Encoder base64Encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
        String base64Body = base64Encoder.encodeToString(derBytes);
        return "-----BEGIN " + keyType + "-----\n" + base64Body + "\n-----END " + keyType + "-----";
    }
}
