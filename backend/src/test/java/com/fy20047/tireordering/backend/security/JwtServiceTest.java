package com.fy20047.tireordering.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fy20047.tireordering.backend.config.JwtProperties;
import com.fy20047.tireordering.backend.entity.Admin;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    @Test
    // 自己生的 token，自己要讀得出來
    void generateToken_shouldIncludeSubjectAndRole() {
        JwtProperties properties = new JwtProperties(
                "0123456789abcdef0123456789abcdef",
                3600,
                0,
                "refresh",
                false,
                "Lax"
        );
        // 測流程 (Service 呼叫 Repository) -> 用 Mockito
        // 測計算/工具 (JWT, Math, StringUtils) -> 直接 new 來測
        JwtService jwtService = new JwtService(properties);

        Admin admin = Admin.builder()
                .username("admin")
                .passwordHash("hash")
                .build();

        String token = jwtService.generateToken(admin); // 生成
        Claims claims = jwtService.parseToken(token); // 讀取

        assertEquals("admin", claims.getSubject()); // 讀出來的名字是 "admin" 嗎
        assertEquals("ADMIN", claims.get("role", String.class)); // 讀出來的職位是 "ADMIN" 嗎
    }

    @Test
    // 收到垃圾或是假的，要報錯
    void parseToken_whenInvalid_shouldThrow() {
        JwtProperties properties = new JwtProperties(
                "0123456789abcdef0123456789abcdef",
                3600,
                0,
                "refresh",
                false,
                "Lax"
        );
        JwtService jwtService = new JwtService(properties);

        // 確保系統不會因為收到奇怪的字串就當機，而是有防護機制
        assertThrows(JwtException.class, () -> jwtService.parseToken("not-a-jwt"));
    }
}
