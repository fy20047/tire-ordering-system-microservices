package com.fy20047.tireordering.orderservice;

import com.fy20047.tireordering.orderservice.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// 這個檔案用途：
// Order Service 的 Spring Boot 啟動入口，負責啟動 IoC 容器與載入同 package 下的元件。
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class OrderServiceApplication {

    // 主程式入口：
    // 交由 Spring Boot 啟動整個 Order Service（後續會承接建單與後台訂單管理 API）。
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
