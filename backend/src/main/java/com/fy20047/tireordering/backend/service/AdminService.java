package com.fy20047.tireordering.backend.service;

import com.fy20047.tireordering.backend.entity.Admin;
import com.fy20047.tireordering.backend.repository.AdminRepository;
import com.fy20047.tireordering.backend.security.JwtService;
import com.fy20047.tireordering.backend.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 管理員登入驗證（密碼比對、產生 token）
@Service
@Transactional(readOnly = true)
public class AdminService {

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

    // 寫登入流程（產 token）- 1. 驗證帳密
    public LoginResult login(String username, String password) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        // 將使用者輸入的密碼去比對資料庫裡的 hash
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String accessToken = jwtService.generateToken(admin);
        String refreshToken = refreshTokenService.createRefreshToken(admin);
        return new LoginResult(accessToken, refreshToken); // 改回傳 access + refresh
    }

    public record LoginResult(String accessToken, String refreshToken) {
    }
}
