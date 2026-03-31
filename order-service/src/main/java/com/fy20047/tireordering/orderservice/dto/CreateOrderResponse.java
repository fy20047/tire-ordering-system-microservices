package com.fy20047.tireordering.orderservice.dto;

import com.fy20047.tireordering.orderservice.enums.OrderStatus;

// 這個檔案用途：
// 定義前台建單成功後的回傳資料格式。
public record CreateOrderResponse(
        // 這段欄位用途：新建立的訂單編號。
        Long orderId,
        // 這段欄位用途：訂單初始狀態。
        OrderStatus status,
        // 這段欄位用途：給前端顯示的提示文字。
        String message
) {
}
