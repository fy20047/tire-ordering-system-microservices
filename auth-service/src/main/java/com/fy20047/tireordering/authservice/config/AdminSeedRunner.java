package com.fy20047.tireordering.authservice.config;

import com.fy20047.tireordering.authservice.entity.Admin;
import com.fy20047.tireordering.authservice.repository.AdminRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// 這個檔案用途：
// Auth Service 啟動時，依環境變數自動建立初始管理員，避免本機或新環境沒有 admin 無法登入。
@Component
public class AdminSeedRunner implements CommandLineRunner {

    // 這段欄位用途：操作 admins 資料表與密碼雜湊。
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeedRunner(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 這段流程用途：
    // 1) 從環境變數讀 ADMIN_USERNAME/ADMIN_PASSWORD
    // 2) 若帳號不存在就建立
    // 3) 若已存在則不重複建立
    @Override
    public void run(String... args) {
        String username = System.getenv("ADMIN_USERNAME");
        String password = System.getenv("ADMIN_PASSWORD");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }

        adminRepository.findByUsername(username).ifPresentOrElse(
                admin -> {
                    // 已存在就略過，避免重複寫入。
                },
                () -> {
                    Admin admin = Admin.builder()
                            .username(username.trim())
                            .passwordHash(passwordEncoder.encode(password))
                            .build();
                    adminRepository.save(admin);
                }
        );
    }
}
