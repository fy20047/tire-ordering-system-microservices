package com.fy20047.tireordering.backend.security;

import com.fy20047.tireordering.backend.config.JwtProperties;
import com.fy20047.tireordering.backend.entity.Admin;
import com.fy20047.tireordering.backend.entity.RefreshToken;
import com.fy20047.tireordering.backend.repository.AdminRepository;
import com.fy20047.tireordering.backend.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
// 產生 refresh token（UUID） -> hash 後寫入 DB -> 驗證 / 撤銷 / 旋轉 refresh token
public class RefreshTokenService {

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

    // 產生 refresh token（UUID）
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

    public RefreshToken validateAndGet(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (token.isRevoked() || token.isExpired()) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        return token;
    }

    public Admin getAdminByUsername(String username) {
        return adminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
    }

    public void revoke(RefreshToken token) {
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
    }

    public void revokeByRawToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(this::revoke);
    }

    // 用 hash 查找 refresh token（驗證用）
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
