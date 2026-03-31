package com.fy20047.tireordering.orderservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 這個檔案用途：
// 封裝 order-service 呼叫 tire-service 的流程，負責查詢輪胎資訊並轉成訂單可用資料模型。
@Component
public class TireServiceClient {

    // 這段欄位用途：保存 tire-service 基礎位址與 JSON 解析工具。
    private final String tireBaseUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TireServiceClient(
            @Value("${integration.tire-service.base-url:${TIRE_BASE_URL:http://tire-service:8080}}") String tireBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.tireBaseUrl = normalizeBaseUrl(tireBaseUrl);
        this.objectMapper = objectMapper;
        // 這段初始化用途：建立可重用的 HTTP client，避免每次請求重複建構。
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    // 這段方法用途：依輪胎 ID 呼叫 tire-service 取得商品資料，供建單寫入 snapshot。
    public TireProduct getTireById(Long tireId) {
        if (tireId == null) {
            throw new IllegalArgumentException("tireId is required");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tireBaseUrl + "/api/tires/" + tireId))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tire verification was interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to call tire-service", ex);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return parseTireResponse(response.body());
        }

        String errorMessage = parseErrorMessage(response.body());
        if (status >= 400 && status < 500) {
            throw new IllegalArgumentException(
                    errorMessage == null || errorMessage.isBlank()
                            ? "Unable to find tire from tire-service"
                            : errorMessage
            );
        }

        throw new IllegalStateException("Failed to verify tire availability via tire-service");
    }

    // 這段方法用途：解析 tire-service 成功回應，轉為內部可用的輪胎資料。
    private TireProduct parseTireResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return new TireProduct(
                    root.path("id").isNull() ? null : root.path("id").asLong(),
                    readNullableText(root, "brand"),
                    readNullableText(root, "series"),
                    readNullableText(root, "origin"),
                    readNullableText(root, "size"),
                    root.path("price").isNull() ? null : root.path("price").asInt(),
                    root.path("active").asBoolean(root.path("isActive").asBoolean(false))
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid tire-service response format", ex);
        }
    }

    // 這段方法用途：解析錯誤回應訊息，對齊現有 ErrorResponse 格式中的 message 欄位。
    private String parseErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return readNullableText(root, "message");
        } catch (IOException ex) {
            return null;
        }
    }

    // 這段方法用途：安全讀取 JSON 字串欄位，避免缺欄位時拋例外。
    private String readNullableText(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    // 這段方法用途：移除 base URL 結尾斜線，避免組 URL 時產生雙斜線。
    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("integration.tire-service.base-url is empty");
        }
        if (rawBaseUrl.endsWith("/")) {
            return rawBaseUrl.substring(0, rawBaseUrl.length() - 1);
        }
        return rawBaseUrl;
    }

    // 這個 record 用途：
    // 提供建單流程所需的輪胎資料快照來源。
    public record TireProduct(
            Long id,
            String brand,
            String series,
            String origin,
            String size,
            Integer price,
            boolean isActive
    ) {
    }
}
