package com.fy20047.tireordering.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fy20047.tireordering.backend.entity.Admin;
import com.fy20047.tireordering.backend.repository.AdminRepository;
import com.fy20047.tireordering.backend.security.JwtService;
import com.fy20047.tireordering.backend.security.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminService adminService;

    @Test
    // 查無此人，不發 Token
    void login_whenUserNotFound_shouldThrow() {
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.login("admin", "password")
        );
        assertEquals("Invalid username or password", ex.getMessage());
        // 檢查 jwtService 是否真的沒有偷偷發 Access Token 與 Refresh Token
        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.ArgumentMatchers.any());
    }

    @Test
    // 密碼錯誤不發 Token
    void login_whenPasswordMismatch_shouldThrow() {
        Admin admin = Admin.builder()
                .username("admin")
                .passwordHash("hash")
                .build();
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin)); // 假設有 admin 這個用戶
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false); // 比對 "wrong" 和 "hash" -> 回傳 false (不吻合)

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.login("admin", "wrong")
        );
        assertEquals("Invalid username or password", ex.getMessage());
        verify(jwtService, never()).generateToken(admin);
        verify(refreshTokenService, never()).createRefreshToken(admin);
    }

    @Test
    // 登入成功，必須同時拿到兩個 Token
    void login_whenValid_shouldReturnToken() {
        Admin admin = Admin.builder()
                .username("admin")
                .passwordHash("hash")
                .build();
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(jwtService.generateToken(admin)).thenReturn("token");
        when(refreshTokenService.createRefreshToken(admin)).thenReturn("refresh");

        AdminService.LoginResult result = adminService.login("admin", "password");

        assertEquals("token", result.accessToken());
        assertEquals("refresh", result.refreshToken());
        verify(jwtService).generateToken(admin);
        verify(refreshTokenService).createRefreshToken(admin);
    }
}
