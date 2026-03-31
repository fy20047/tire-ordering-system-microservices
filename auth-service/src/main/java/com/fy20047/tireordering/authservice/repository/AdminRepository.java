package com.fy20047.tireordering.authservice.repository;

import com.fy20047.tireordering.authservice.entity.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 這個檔案用途：
// 提供管理員帳號查詢能力，供登入流程依 username 驗證帳密。
public interface AdminRepository extends JpaRepository<Admin, Long> {

    // 這段方法用途：用帳號查管理員，登入流程的主要查詢入口。
    Optional<Admin> findByUsername(String username);
}
