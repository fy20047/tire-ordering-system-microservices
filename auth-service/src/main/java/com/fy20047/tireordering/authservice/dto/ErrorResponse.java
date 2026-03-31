package com.fy20047.tireordering.authservice.dto;

import java.util.Map;

// 這個檔案用途：
// 定義 Auth Service 對外統一錯誤格式，讓前端與 smoke 測試能穩定解析錯誤回應。
public record ErrorResponse(
        // 這段欄位用途：人類可讀的錯誤摘要訊息。
        String message,
        // 這段欄位用途：欄位級錯誤明細（可為 null）。
        Map<String, String> details
) {
}
