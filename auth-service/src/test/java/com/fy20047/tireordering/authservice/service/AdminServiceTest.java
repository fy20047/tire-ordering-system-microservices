package com.fy20047.tireordering.authservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fy20047.tireordering.authservice.entity.Admin;
import com.fy20047.tireordering.authservice.repository.AdminRepository;
import com.fy20047.tireordering.authservice.security.JwtService;
import com.fy20047.tireordering.authservice.security.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

// 這個測試檔案用途：
// 驗證 AdminService 的登入流程：失敗時不得發 token，成功時需回傳 access/refresh token。
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    // 這段欄位用途：隔離外部依賴，僅測登入流程本身。
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

    // 這個測試案例用途：
    // 帳號不存在時應拋錯，且不得簽發任何 token。
    @Test
    void login_whenUserNotFound_shouldThrow() {
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.login("admin", "password")
        );
        assertEquals("Invalid username or password", ex.getMessage());
        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenService, never()).createRefreshToken(org.mockito.ArgumentMatchers.any());
    }

    // 這個測試案例用途：
    // 密碼比對失敗時應拋錯，且不得發 token。
    @Test
    void login_whenPasswordMismatch_shouldThrow() {
        Admin admin = Admin.builder()
                .username("admin")
                .passwordHash("hash")
                .build();
        when(adminRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.login("admin", "wrong")
        );
        assertEquals("Invalid username or password", ex.getMessage());
        verify(jwtService, never()).generateToken(admin);
        verify(refreshTokenService, never()).createRefreshToken(admin);
    }

    // 這個測試案例用途：
    // 登入成功時必須同時回傳 access token 與 refresh token。
    @Test
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
