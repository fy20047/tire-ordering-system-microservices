package com.fy20047.tireordering.authservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 這個檔案用途：
// 定義 Auth Service 的 CORS 規則，讓本機前端或 Gateway 轉發情境能帶 cookie 呼叫 Auth API。
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // 這段設定用途：
    // 放行 /api/** 路徑，允許常見開發來源與跨站 cookie（refresh token）傳遞。
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:5173", "http://127.0.0.1:*", "http://kuang-i-tire")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
