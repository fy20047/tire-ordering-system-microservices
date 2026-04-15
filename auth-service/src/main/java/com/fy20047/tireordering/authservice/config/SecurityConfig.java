package com.fy20047.tireordering.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// 這個檔案用途：
// 定義 Auth Service 的 Spring Security 規則，確保 login/refresh/logout 與健康檢查可被匿名呼叫。
@Configuration
public class SecurityConfig {

    // 這段設定用途：
    // 1) 採用 stateless（不使用伺服器 session）
    // 2) 放行 Auth API 與 health/info
    // 3) 其他請求先維持放行（Phase 2 此服務僅承接 Auth 入口）
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/admin/login",
                                "/api/admin/refresh",
                                "/api/admin/logout",
                                "/api/health",
                                "/actuator/health",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    // 這段設定用途：
    // 提供密碼雜湊比對器，登入驗證時用來比對明文密碼與資料庫 hash。
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
