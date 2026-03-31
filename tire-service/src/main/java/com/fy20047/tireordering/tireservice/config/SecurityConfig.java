package com.fy20047.tireordering.tireservice.config;

import com.fy20047.tireordering.tireservice.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// 這個檔案用途：
// 定義 Tire Service 的授權規則：公開輪胎查詢可匿名，後台輪胎管理需 ADMIN 角色。
@Configuration
public class SecurityConfig {

    // 這段設定用途：
    // 1) 使用 stateless JWT 驗證
    // 2) 放行公開輪胎 API 與 health endpoint
    // 3) 限制 /api/admin/** 需 ROLE_ADMIN
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/tires/**",
                                "/api/health",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
