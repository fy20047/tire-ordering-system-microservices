package com.fy20047.tireordering.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 這個類別是 API Gateway 專案的啟動入口。
// 服務啟動後，會載入本專案的 Controller/Config，開始接收 API 請求。
@SpringBootApplication
public class ApiGatewayApplication {

    // 主程式入口：交給 Spring Boot 啟動整個應用程式。
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
