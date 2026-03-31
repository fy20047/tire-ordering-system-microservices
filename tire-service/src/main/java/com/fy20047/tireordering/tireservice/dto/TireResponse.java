package com.fy20047.tireordering.tireservice.dto;

// 這個檔案用途：
// 定義單一輪胎的 API 回應格式，提供前台顯示商品卡片與詳細資料使用。
public record TireResponse(
        Long id,
        String brand,
        String series,
        String origin,
        String size,
        Integer price,
        boolean isActive
) {
}
