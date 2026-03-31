package com.fy20047.tireordering.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 這個檔案用途：
// 定義管理員登入 API 請求格式，限制欄位必填與長度，避免無效輸入進入商業邏輯。
public record AdminLoginRequest(
        // 這段欄位用途：管理員帳號（必填，最多 100 字元）。
        @NotBlank @Size(max = 100) String username,
        // 這段欄位用途：管理員密碼（必填，最多 100 字元）。
        @NotBlank @Size(max = 100) String password
) {
}
