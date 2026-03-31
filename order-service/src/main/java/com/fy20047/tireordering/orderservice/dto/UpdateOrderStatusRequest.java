package com.fy20047.tireordering.orderservice.dto;

import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

// 這個檔案用途：
// 定義後台更新訂單狀態 API 的請求格式。
public record UpdateOrderStatusRequest(
        // 這段欄位用途：更新後的目標狀態。
        @NotNull OrderStatus status
) {
}
