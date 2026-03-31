package com.fy20047.tireordering.tireservice.dto;

import jakarta.validation.constraints.NotNull;

// 這個檔案用途：
// 定義後台上下架切換請求，只允許更新 `isActive` 欄位。
public record UpdateTireStatusRequest(@NotNull Boolean isActive) {
}
