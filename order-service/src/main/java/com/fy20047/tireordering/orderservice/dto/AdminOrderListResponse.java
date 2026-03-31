package com.fy20047.tireordering.orderservice.dto;

import java.util.List;

// 這個檔案用途：
// 定義後台訂單列表回傳外層格式，方便前端維持固定欄位結構。
public record AdminOrderListResponse(
        // 這段欄位用途：後台訂單清單內容。
        List<AdminOrderResponse> items
) {
}
