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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 這個 Controller 是 Phase 1 的 API 入口代理：
// 1) 接收所有 /api/** 請求
// 2) 轉發到舊 backend
// 3) 盡量維持原本狀態碼/headers/body，讓前端無感
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

    // Java 內建 HttpClient，用來把請求送到舊 backend。
    private final HttpClient httpClient;

    // 舊 backend 的 base URL，可由環境變數覆蓋。
    private final String backendBaseUrl;

    // 建構子注入設定值並初始化 HttpClient。
    public ApiProxyController(@Value("${gateway.backend-base-url:http://backend:8080}") String backendBaseUrl) {
        this.backendBaseUrl = normalizeBaseUrl(backendBaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // 接收 /api/** 的所有 HTTP 方法，原樣代理到舊 backend。
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

        // 送出請求到舊 backend，取得原始回應。
        HttpResponse<byte[]> backendResponse = httpClient.send(
                outboundBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        // 將 backend 回應 headers 複製回前端。
        HttpHeaders responseHeaders = new HttpHeaders();
        backendResponse.headers().map().forEach((name, values) -> {
            if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                responseHeaders.put(name, values);
            }
        });

        // 回傳與 backend 一致的狀態碼 + body + headers。
        return ResponseEntity.status(backendResponse.statusCode())
                .headers(responseHeaders)
                .body(backendResponse.body());
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

    // 組出目標 URI：backendBaseUrl + 原始 URI path + query。
    private URI buildTargetUri(HttpServletRequest request) {
        StringBuilder uriBuilder = new StringBuilder(backendBaseUrl)
                .append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uriBuilder.append("?").append(request.getQueryString());
        }
        return URI.create(uriBuilder.toString());
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
}
