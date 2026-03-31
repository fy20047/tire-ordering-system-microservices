package com.fy20047.tireordering.authservice.dto;

// 這個檔案用途：
// 定義登入/刷新成功後回傳給前端的資料格式（access token + 有效秒數）。
public record AdminLoginResponse(
        // 這段欄位用途：給前端放在 Authorization header 的 access token。
        String token,
        // 這段欄位用途：token 有效秒數，方便前端做重新整理策略。
        long expiresInSeconds
) {
}
