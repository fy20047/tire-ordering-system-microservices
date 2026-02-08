package com.fy20047.tireordering.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // 確保只在測試時使用 H2
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
