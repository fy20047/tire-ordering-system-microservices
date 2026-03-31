package com.fy20047.tireordering.authservice.service;

import com.fy20047.tireordering.authservice.entity.Admin;
import com.fy20047.tireordering.authservice.repository.AdminRepository;
import com.fy20047.tireordering.authservice.security.JwtService;
import com.fy20047.tireordering.authservice.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 這個檔案用途：
// 封裝管理員登入驗證流程（帳號查找、密碼比對、access/refresh token 產生）。
@Service
@Transactional(readOnly = true)
public class AdminService {

    // 這段欄位用途：登入流程依賴的資料查詢、密碼驗證與 token 服務。
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AdminService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    // 這段方法用途：
    // 驗證登入帳密，成功後回傳 access token 與 refresh token 給 controller。
    public LoginResult login(String username, String password) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String accessToken = jwtService.generateToken(admin);
        String refreshToken = refreshTokenService.createRefreshToken(admin);
        return new LoginResult(accessToken, refreshToken);
    }

    // 這段資料結構用途：把登入結果（access/refresh）一次回傳給上層。
    public record LoginResult(String accessToken, String refreshToken) {
    }
}
