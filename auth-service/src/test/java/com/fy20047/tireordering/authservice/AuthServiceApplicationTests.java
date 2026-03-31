package com.fy20047.tireordering.authservice;

import com.fy20047.tireordering.authservice.support.TestRsaKeyPairFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 這個測試檔案用途：
// 驗證 Auth Service 在測試設定下可以成功啟動 Spring Context。
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    // 這段常數用途：
    // 在 context 測試啟動前動態提供有效 RSA 金鑰，避免使用 repository 內固定私鑰。
    private static final TestRsaKeyPairFactory.PemKeyPair TEST_KEY_PAIR = TestRsaKeyPairFactory.generate();

    // 這個方法用途：
    // 將動態生成的 RSA 金鑰注入測試環境設定，確保 JwtService 初始化成功。
    @DynamicPropertySource
    static void registerJwtProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.private-key", TEST_KEY_PAIR::privateKeyPem);
        registry.add("security.jwt.public-key", TEST_KEY_PAIR::publicKeyPem);
    }

    // 這個測試案例用途：
    // 至少確認 Bean 載入與設定綁定沒有明顯錯誤。
    @Test
    void contextLoads() {
    }
}
