package com.fy20047.tireordering.tireservice;

import com.fy20047.tireordering.tireservice.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// 這個檔案用途：
// Tire Service 的 Spring Boot 啟動入口，負責啟動 IoC 容器與載入同 package 下的元件。
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class TireServiceApplication {

    // 主程式入口：
    // 交由 Spring Boot 啟動整個 Tire Service（後續會承接輪胎查詢與後台管理 API）。
    public static void main(String[] args) {
        SpringApplication.run(TireServiceApplication.class, args);
    }
}
