package com.fy20047.tireordering.apigateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 這個 Controller 的用途是提供 Gateway 自身的 `/api/health` 相容端點。
 * 目的：在移除 backend fallback 前，先讓既有 smoke 與操作文件不需要改呼叫路徑。
 */
@RestController
public class GatewayHealthController {

    // 這段用途：讀取 Spring Boot Actuator 的健康狀態，避免手動重複判斷邏輯。
    private final HealthEndpoint healthEndpoint;

    public GatewayHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    // 這段用途：提供與既有 `/api/health` 相容的健康檢查 JSON，回應碼依狀態決定 200/503。
    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> apiHealth() {
        // 這段用途：取得 Actuator 健康結果，並轉成 API 回傳格式。
        HealthComponent healthComponent = healthEndpoint.health();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", healthComponent.getStatus().getCode());
        body.put("service", "api-gateway");

        // 這段用途：若 Actuator 有細節資訊則一併回傳，方便排查。
        if (healthComponent instanceof Health health) {
            if (!health.getDetails().isEmpty()) {
                body.put("details", health.getDetails());
            }
        }

        // 這段用途：沿用健康檢查慣例，UP 回 200，其餘狀態回 503。
        HttpStatus status = Status.UP.equals(healthComponent.getStatus())
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }
}
