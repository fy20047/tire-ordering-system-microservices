package com.fy20047.tireordering.apigateway;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// 這個 Controller 是 API 入口代理：
// 1) 接收所有 /api/** 請求
// 2) 依路徑轉發到對應服務（Auth -> auth-service、Tire -> tire-service、Order -> order-service）
// 3) 未匹配路徑直接回 404，不再 fallback 到 monolith backend
@RestController
@RequestMapping("/api")
public class ApiProxyController {

    // 這些是 hop-by-hop 或會由下游框架自動計算的 header，不直接轉發。
    private static final Set<String> EXCLUDED_REQUEST_HEADERS = Set.of(
            HttpHeaders.HOST.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            // Java HttpClient 對 Expect 屬於限制型 header，直接轉發會拋例外
            HttpHeaders.EXPECT.toLowerCase(Locale.ROOT)
    );

    // 回應時同樣排除連線層 header，避免傳輸層衝突。
    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = Set.of(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT)
    );

    // Java 內建 HttpClient，用來把請求送到下游微服務。
    private final HttpClient httpClient;

    // auth-service 的 base URL（login/refresh/logout 走這條）。
    private final String authBaseUrl;
    // tire-service 的 base URL（輪胎查詢與後台輪胎管理走這條）。
    private final String tireBaseUrl;
    // order-service 的 base URL（建單與後台訂單管理走這條）。
    private final String orderBaseUrl;

    // 建構子注入設定值並初始化 HttpClient。
    public ApiProxyController(
            @Value("${gateway.auth-base-url:http://auth-service:8080}") String authBaseUrl,
            @Value("${gateway.tire-base-url:http://tire-service:8080}") String tireBaseUrl,
            @Value("${gateway.order-base-url:http://order-service:8080}") String orderBaseUrl
    ) {
        this.authBaseUrl = normalizeBaseUrl(authBaseUrl);
        this.tireBaseUrl = normalizeBaseUrl(tireBaseUrl);
        this.orderBaseUrl = normalizeBaseUrl(orderBaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 接收 /api/** 的所有 HTTP 方法，依路徑代理到對應微服務。
    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) throws IOException, InterruptedException {
        // 組出要轉發的目標 URI（保留 path + query string）。
        URI targetUri = buildTargetUri(request);

        // 建立 outbound request，方法與 body 皆沿用原請求。
        HttpRequest.Builder outboundBuilder = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofSeconds(30))
                .method(request.getMethod(), buildBodyPublisher(request.getMethod(), body));

        // 複製可轉發的 request headers。
        copyRequestHeaders(request, outboundBuilder);

        // 送出請求到下游微服務，取得原始回應。
        HttpResponse<byte[]> downstreamResponse = httpClient.send(
                outboundBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        // 將下游回應 headers 複製回前端。
        HttpHeaders responseHeaders = new HttpHeaders();
        downstreamResponse.headers().map().forEach((name, values) -> {
            if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                responseHeaders.put(name, values);
            }
        });

        // 回傳與下游一致的狀態碼 + body + headers。
        return ResponseEntity.status(downstreamResponse.statusCode())
                .headers(responseHeaders)
                .body(downstreamResponse.body());
    }

    // 判斷是否需要 request body；GET/HEAD/OPTIONS 預設不送 body。
    private HttpRequest.BodyPublisher buildBodyPublisher(String method, byte[] body) {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        boolean supportsBody = httpMethod != HttpMethod.GET
                && httpMethod != HttpMethod.HEAD
                && httpMethod != HttpMethod.OPTIONS;
        if (!supportsBody || body == null || body.length == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(body);
    }

    // 組出目標 URI：依路徑決定 base URL，再拼上原始 URI path + query。
    private URI buildTargetUri(HttpServletRequest request) {
        String targetBaseUrl = resolveTargetBaseUrl(request.getRequestURI());
        StringBuilder uriBuilder = new StringBuilder(targetBaseUrl)
                .append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uriBuilder.append("?").append(request.getQueryString());
        }
        return URI.create(uriBuilder.toString());
    }

    // 依 API 路徑決定要轉發到哪個服務：
    // - /api/admin/login|refresh|logout -> auth-service
    // - /api/tires/** 與 /api/admin/tires/** -> tire-service
    // - /api/orders/** 與 /api/admin/orders/** -> order-service
    // - 其餘 -> 直接回 404（Phase 5 已關閉 backend fallback）
    private String resolveTargetBaseUrl(String requestUri) {
        if (isAuthEntryPath(requestUri)) {
            return authBaseUrl;
        }
        if (isTirePath(requestUri)) {
            return tireBaseUrl;
        }
        if (isOrderPath(requestUri)) {
            return orderBaseUrl;
        }
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Gateway route not found for path: " + requestUri
        );
    }

    // 僅匹配 Auth 對外入口，避免把 admin 業務 API（如 /api/admin/orders）誤導到 auth-service。
    private boolean isAuthEntryPath(String requestUri) {
        String normalizedPath = normalizePath(requestUri);
        return normalizedPath.equals("/api/admin/login")
                || normalizedPath.equals("/api/admin/refresh")
                || normalizedPath.equals("/api/admin/logout");
    }

    // 僅匹配 Tire API 入口，避免把其他 admin 業務 API 誤導到 tire-service。
    private boolean isTirePath(String requestUri) {
        String normalizedPath = normalizePath(requestUri);
        return matchesPathPrefix(normalizedPath, "/api/tires")
                || matchesPathPrefix(normalizedPath, "/api/admin/tires");
    }

    // 僅匹配 Order API 入口，避免把其他業務 API 誤導到 order-service。
    private boolean isOrderPath(String requestUri) {
        String normalizedPath = normalizePath(requestUri);
        return matchesPathPrefix(normalizedPath, "/api/orders")
                || matchesPathPrefix(normalizedPath, "/api/admin/orders");
    }

    // 路徑比對工具：只接受完整段落匹配（prefix 本身或 prefix + "/"）。
    private boolean matchesPathPrefix(String normalizedPath, String prefix) {
        return normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/");
    }

    // 複製 inbound headers 到 outbound，排除不應直接轉發的 header。
    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder outboundBuilder) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (EXCLUDED_REQUEST_HEADERS.contains(headerName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                outboundBuilder.header(headerName, headerValues.nextElement());
            }
        }
    }

    // 去除結尾斜線，避免後續組 URI 產生雙斜線。
    private String normalizeBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl.endsWith("/")) {
            return rawBaseUrl.substring(0, rawBaseUrl.length() - 1);
        }
        return rawBaseUrl;
    }

    // 去除 path 結尾斜線，避免路徑比對時出現 /a 與 /a/ 不一致。
    private String normalizePath(String requestUri) {
        if (requestUri.endsWith("/") && requestUri.length() > 1) {
            return requestUri.substring(0, requestUri.length() - 1);
        }
        return requestUri;
    }
}
