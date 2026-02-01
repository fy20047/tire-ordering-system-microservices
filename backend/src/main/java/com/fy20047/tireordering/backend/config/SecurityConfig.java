package com.fy20047.tireordering.backend.config;

import com.fy20047.tireordering.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Spring Security 設定（放行哪些 API、JWT filter、設定哪些 API 要登入）
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/admin/login", // /api/admin/login 允許匿名
                                "/api/admin/refresh", // 讓 refresh 和 logout 不需要 access token 就能被呼叫，
                                "/api/admin/logout", // 因為當 access token 過期時，refresh API 會被 JWT filter 擋掉，必須放行改用 refresh cookie 驗證
                                "/api/tires/**",
                                "/api/orders/**", // /api/tires、/api/orders 允許公開
                                "/api/health"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // /api/admin/** 需要 ROLE_ADMIN
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // JWT filter 必須放在 UsernamePasswordAuthenticationFilter 之前

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
