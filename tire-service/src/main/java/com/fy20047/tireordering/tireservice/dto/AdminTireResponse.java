package com.fy20047.tireordering.tireservice.dto;

import java.time.LocalDateTime;

// 這個檔案用途：
// 定義後台輪胎單筆回應格式，包含管理端需要的時間欄位。
public record AdminTireResponse(
        Long id,
        String brand,
        String series,
        String origin,
        String size,
        Integer price,
        boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
