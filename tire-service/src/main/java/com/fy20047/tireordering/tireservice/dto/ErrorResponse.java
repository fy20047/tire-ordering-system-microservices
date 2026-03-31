package com.fy20047.tireordering.tireservice.dto;

import java.util.Map;

// 這個檔案用途：
// 定義統一錯誤回應格式，讓前端可以用固定欄位處理錯誤訊息。
public record ErrorResponse(
        String message,
        Map<String, String> details
) {
}
