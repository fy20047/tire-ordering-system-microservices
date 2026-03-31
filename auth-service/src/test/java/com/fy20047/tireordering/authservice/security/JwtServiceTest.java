package com.fy20047.tireordering.authservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fy20047.tireordering.authservice.config.JwtProperties;
import com.fy20047.tireordering.authservice.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

// 這個測試檔案用途：
// 驗證 JwtService 的核心行為：能簽發可解析 token，且能拒絕非法 token。
class JwtServiceTest {

    // 這段常數用途：提供 RS256 測試金鑰（與 application-test.yaml 同組），驗證簽章/驗章流程。
    private static final String TEST_PRIVATE_KEY = """
            __REMOVED_PRIVATE_KEY__
            """;

    private static final String TEST_PUBLIC_KEY = """
            __REMOVED_PUBLIC_KEY__
            """;

    // 這個測試案例用途：
    // 確認 generateToken 後可由 parseToken 讀回 subject 與 role。
    @Test
    void generateToken_shouldIncludeSubjectAndRole() {
        JwtProperties properties = new JwtProperties(
                TEST_PRIVATE_KEY,
                TEST_PUBLIC_KEY,
                3600,
                1209600,
                "refreshToken",
                false,
                "Lax"
        );
        JwtService jwtService = new JwtService(properties);

        Admin admin = Admin.builder()
                .username("admin")
                .passwordHash("hash")
                .build();

        String token = jwtService.generateToken(admin);
        Claims claims = jwtService.parseToken(token);

        assertEquals("admin", claims.getSubject());
        assertEquals("ADMIN", claims.get("role", String.class));
    }

    // 這個測試案例用途：
    // 確認收到非 JWT 字串時，服務會丟出例外而非誤判為合法。
    @Test
    void parseToken_whenInvalid_shouldThrow() {
        JwtProperties properties = new JwtProperties(
                TEST_PRIVATE_KEY,
                TEST_PUBLIC_KEY,
                3600,
                1209600,
                "refreshToken",
                false,
                "Lax"
        );
        JwtService jwtService = new JwtService(properties);

        assertThrows(JwtException.class, () -> jwtService.parseToken("not-a-jwt"));
    }
}
