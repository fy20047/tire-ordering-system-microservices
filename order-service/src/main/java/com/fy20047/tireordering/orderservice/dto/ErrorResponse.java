package com.fy20047.tireordering.orderservice.dto;

import java.util.Map;

// 這個檔案用途：
// 定義 API 統一錯誤回傳格式，讓前後端錯誤處理行為一致。
public record ErrorResponse(
        // 這段欄位用途：主要錯誤訊息。
        String message,
        // 這段欄位用途：欄位級細節錯誤（可為 null）。
        Map<String, String> details
) {
}
