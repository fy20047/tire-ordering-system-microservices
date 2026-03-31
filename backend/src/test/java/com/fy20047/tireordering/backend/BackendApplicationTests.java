package com.fy20047.tireordering.backend;

import com.fy20047.tireordering.backend.support.TestRsaKeyPairFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test") // 確保只在測試時使用 H2
class BackendApplicationTests {

	// 這段常數用途：
	// context 測試啟動時動態提供 RSA 金鑰，避免把私鑰內容固定寫在測試設定檔。
	private static final TestRsaKeyPairFactory.PemKeyPair TEST_KEY_PAIR = TestRsaKeyPairFactory.generate();

	// 這個方法用途：
	// 在 Spring Context 啟動前注入 JWT 金鑰設定，確保 JwtService 可正常初始化。
	@DynamicPropertySource
	static void registerJwtProperties(DynamicPropertyRegistry registry) {
		registry.add("security.jwt.private-key", TEST_KEY_PAIR::privateKeyPem);
		registry.add("security.jwt.public-key", TEST_KEY_PAIR::publicKeyPem);
	}

	@Test
	void contextLoads() {
	}

}
