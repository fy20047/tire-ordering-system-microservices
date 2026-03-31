package com.fy20047.tireordering.authservice.repository;

import com.fy20047.tireordering.authservice.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 這個檔案用途：
// 提供 refresh token 查詢能力，供 refresh/logout 依 token hash 驗證與撤銷。
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 這段方法用途：用 token hash 查 refresh token 紀錄。
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
