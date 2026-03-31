package com.fy20047.tireordering.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 這個測試檔案用途：
// 驗證 Auth Service 在測試設定下可以成功啟動 Spring Context。
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    // 這個測試案例用途：
    // 至少確認 Bean 載入與設定綁定沒有明顯錯誤。
    @Test
    void contextLoads() {
    }
}
