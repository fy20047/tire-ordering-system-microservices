package com.fy20047.tireordering.apigateway;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 這個 Controller 提供 Gateway 的根路徑說明頁，並提供登入頁的轉址入口。
 * 目的：避免使用者打開 http://localhost:8080 時看到 Whitelabel Error Page。
 */
@RestController
public class GatewayInfoController {

    // 前端登入頁 URL，可由環境變數 FRONTEND_LOGIN_URL 覆蓋。
    private final String frontendLoginUrl;

    public GatewayInfoController(
            @Value("${gateway.frontend-login-url:http://localhost:5173/admin/login}")
            String frontendLoginUrl
    ) {
        this.frontendLoginUrl = frontendLoginUrl;
    }

    // 根路徑說明頁：告訴使用者 API、前端與登入頁的建議入口。
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> rootInfo() {
        String html = """
                <!doctype html>
                <html lang="zh-Hant">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>API Gateway 說明頁</title>
                  <style>
                    body { font-family: "Noto Sans TC", "Microsoft JhengHei", sans-serif; margin: 32px; line-height: 1.7; }
                    code { background: #f3f4f6; padding: 2px 6px; border-radius: 6px; }
                    .box { border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px; margin-top: 16px; }
                    a { color: #2563eb; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                  </style>
                </head>
                <body>
                  <h1>API Gateway 已啟動</h1>
                  <p>這個服務只負責 API 轉發，不提供完整前端畫面。</p>
                  <div class="box">
                    <p><strong>API 健康檢查：</strong><a href="/api/health"><code>/api/health</code></a></p>
                    <p><strong>登入頁入口：</strong><a href="/login"><code>/login</code></a>（會轉址到前端登入頁）</p>
                    <p><strong>前端首頁（本機）：</strong><a href="http://localhost:5173/">http://localhost:5173/</a></p>
                  </div>
                </body>
                </html>
                """;
        return ResponseEntity.ok(html);
    }

    // 登入頁轉址：統一從 Gateway 導向前端登入頁，方便本機與未來部署環境調整。
    @GetMapping("/login")
    public ResponseEntity<Void> redirectToLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendLoginUrl))
                .build();
    }
}
