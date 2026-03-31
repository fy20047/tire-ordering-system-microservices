package com.fy20047.tireordering.authservice.controller;

import com.fy20047.tireordering.authservice.config.JwtProperties;
import com.fy20047.tireordering.authservice.dto.AdminLoginRequest;
import com.fy20047.tireordering.authservice.dto.AdminLoginResponse;
import com.fy20047.tireordering.authservice.security.JwtService;
import com.fy20047.tireordering.authservice.security.RefreshTokenService;
import com.fy20047.tireordering.authservice.service.AdminService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 這個檔案用途：
// 提供 Auth Service 的三個核心 API：/login、/refresh、/logout。
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    // 這段欄位用途：登入驗證、JWT 設定、refresh token 管理與 access token 產生。
    private final AdminService adminService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    public AdminAuthController(
            AdminService adminService,
            JwtProperties jwtProperties,
            RefreshTokenService refreshTokenService,
            JwtService jwtService
    ) {
        this.adminService = adminService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
    }

    // 這段 API 用途：
    // 驗證帳密，成功後回傳 access token，並在 HttpOnly cookie 寫入 refresh token。
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletResponse response
    ) {
        AdminService.LoginResult result = adminService.login(request.username(), request.password());
        AdminLoginResponse body = new AdminLoginResponse(result.accessToken(), jwtProperties.expirationSeconds());
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(body);
    }

    // 這段 API 用途：
    // 用 refresh cookie 換新 access token，並同步旋轉 refresh token。
    @PostMapping("/refresh")
    public ResponseEntity<AdminLoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        var tokenEntity = refreshTokenService.validateAndGet(refreshToken);
        var admin = tokenEntity.getAdmin();
        refreshTokenService.revoke(tokenEntity);

        String newRefreshToken = refreshTokenService.createRefreshToken(admin);
        String newAccessToken = jwtService.generateToken(admin);

        setRefreshCookie(response, newRefreshToken);
        AdminLoginResponse body = new AdminLoginResponse(newAccessToken, jwtProperties.expirationSeconds());
        return ResponseEntity.ok(body);
    }

    // 這段 API 用途：
    // 撤銷 refresh token 並清除 cookie，讓後續 refresh 失效。
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken != null) {
            refreshTokenService.revokeByRawToken(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok().build();
    }

    // 這段方法用途：從請求 cookie 陣列抽出 refresh token。
    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> jwtProperties.refreshCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    // 這段方法用途：設定 refresh token cookie（HttpOnly + SameSite + 可選 Secure）。
    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(
                "Set-Cookie",
                String.format(
                        "%s=%s; Path=/api/admin; Max-Age=%d; HttpOnly; SameSite=%s%s",
                        jwtProperties.refreshCookieName(),
                        refreshToken,
                        jwtProperties.refreshExpirationSeconds(),
                        jwtProperties.refreshCookieSameSite(),
                        jwtProperties.refreshCookieSecure() ? "; Secure" : ""
                )
        );
    }

    // 這段方法用途：將 refresh cookie 立即失效（Max-Age=0）。
    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(
                "Set-Cookie",
                String.format(
                        "%s=; Path=/api/admin; Max-Age=0; HttpOnly; SameSite=%s%s",
                        jwtProperties.refreshCookieName(),
                        jwtProperties.refreshCookieSameSite(),
                        jwtProperties.refreshCookieSecure() ? "; Secure" : ""
                )
        );
    }
}
