package com.fy20047.tireordering.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 這個檔案用途：
// Auth Service 的 Spring Boot 啟動入口，負責啟動 IoC 容器與載入同 package 下的元件。
@SpringBootApplication
public class AuthServiceApplication {

    // 主程式入口：
    // 交由 Spring Boot 啟動整個 Auth Service（包含 Controller/Service/Repository）。
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
