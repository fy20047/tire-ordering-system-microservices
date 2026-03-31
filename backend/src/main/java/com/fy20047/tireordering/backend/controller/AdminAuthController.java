package com.fy20047.tireordering.backend.controller;

import com.fy20047.tireordering.backend.config.JwtProperties;
import com.fy20047.tireordering.backend.dto.AdminLoginRequest;
import com.fy20047.tireordering.backend.dto.AdminLoginResponse;
import com.fy20047.tireordering.backend.security.JwtService;
import com.fy20047.tireordering.backend.security.RefreshTokenService;
import com.fy20047.tireordering.backend.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 寫登入流程（產 token）- 3. /api/admin/login 登入 API
@RestController
@RequestMapping("/api/admin")
@ConditionalOnProperty(
        name = "feature.backend-auth-endpoints-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdminAuthController {

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

    // 登入成功回傳 access token + 設 httpOnly refresh cookie
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

    // 用 cookie 交換新 access token（並旋轉 refresh token）
    @PostMapping("/refresh")
    public ResponseEntity<AdminLoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) {
            return ResponseEntity.status(401).build();
        }

        var tokenEntity = refreshTokenService.validateAndGet(refreshToken);
        var admin = tokenEntity.getAdmin();
        refreshTokenService.revoke(tokenEntity); // rotate
        String newRefreshToken = refreshTokenService.createRefreshToken(admin);
        String newAccessToken = jwtService.generateToken(admin);

        setRefreshCookie(response, newRefreshToken);
        AdminLoginResponse body = new AdminLoginResponse(newAccessToken, jwtProperties.expirationSeconds());
        return ResponseEntity.ok(body);
    }

    // 撤銷 refresh token + 清除 cookie
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        if (refreshToken != null) {
            refreshTokenService.revokeByRawToken(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok().build();
    }

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

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(jwtProperties.refreshCookieName(), refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(jwtProperties.refreshCookieSecure());
        cookie.setPath("/api/admin");
        cookie.setMaxAge(Math.toIntExact(jwtProperties.refreshExpirationSeconds()));
        // SameSite 需要用 header 設定 (Servlet Cookie 不支援)
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
