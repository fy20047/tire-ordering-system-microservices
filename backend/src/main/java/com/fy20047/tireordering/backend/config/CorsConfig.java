package com.fy20047.tireordering.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // 設定檔（讓 Spring 啟動時先讀取）
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // 這個規則適用於所有以 /api/ 開頭的網址
                .allowedOriginPatterns("http://localhost:5173", "http://127.0.0.1:*", "http://kuang-i-tire") // 白名單 (Guest List)：Vite (React) 預設的啟動 Port
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS") // 允許前端對後端做什麼動作
                .allowedHeaders("*") // 允許前端帶任何 Header 過來（例如身分驗證的 Token）
                .allowCredentials(true); // 允許帶 cookie (refresh token)，即允許 cookie 傳遞
    }
}
