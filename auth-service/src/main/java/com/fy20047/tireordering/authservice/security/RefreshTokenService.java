package com.fy20047.tireordering.authservice.security;

import com.fy20047.tireordering.authservice.config.JwtProperties;
import com.fy20047.tireordering.authservice.entity.Admin;
import com.fy20047.tireordering.authservice.entity.RefreshToken;
import com.fy20047.tireordering.authservice.repository.AdminRepository;
import com.fy20047.tireordering.authservice.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 這個檔案用途：
// 管理 refresh token 全生命週期：建立、驗證、撤銷、旋轉（rotate）。
@Service
@Transactional
public class RefreshTokenService {

    // 這段欄位用途：refresh token 查詢、admin 查詢與 JWT 相關時效設定。
    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminRepository adminRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AdminRepository adminRepository,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.adminRepository = adminRepository;
        this.jwtProperties = jwtProperties;
    }

    // 這段方法用途：建立新 refresh token，資料庫僅保存 hash，原文只回傳給呼叫端寫入 cookie。
    public String createRefreshToken(Admin admin) {
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String tokenHash = hashToken(rawToken);
        RefreshToken refreshToken = RefreshToken.builder()
                .admin(admin)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.refreshExpirationSeconds()))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    // 這段方法用途：驗證 refresh token 是否存在、未撤銷、未過期。
    public RefreshToken validateAndGet(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (token.isRevoked() || token.isExpired()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        return token;
    }

    // 這段方法用途：依 username 查 admin（保留給後續流程使用）。
    public Admin getAdminByUsername(String username) {
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
    }

    // 這段方法用途：標記單一 refresh token 已撤銷。
    public void revoke(RefreshToken token) {
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
    }

    // 這段方法用途：依原始 token 內容查到資料後執行撤銷。
    public void revokeByRawToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(this::revoke);
    }

    // 這段方法用途：把原始 token 轉成 SHA-256 hash，避免資料庫存放可直接使用的原文 token。
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available");
        }
    }
}
