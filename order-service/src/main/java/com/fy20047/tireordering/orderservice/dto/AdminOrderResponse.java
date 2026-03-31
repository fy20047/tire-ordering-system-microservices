package com.fy20047.tireordering.orderservice.dto;

import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import java.time.LocalDateTime;

// 這個檔案用途：
// 定義後台單筆訂單回應格式，包含訂單欄位與目前關聯的輪胎欄位（過渡版）。
public record AdminOrderResponse(
        // 這段欄位用途：訂單主要資訊。
        Long id,
        OrderStatus status,
        Integer quantity,
        String customerName,
        String phone,
        String email,
        InstallationOption installationOption,
        String deliveryAddress,
        String carModel,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // 這段欄位用途：輪胎欄位（Phase 4 Snapshot 完成後會改為 snapshot 欄位來源）。
        Long tireId,
        String tireBrand,
        String tireSeries,
        String tireOrigin,
        String tireSize,
        Integer tirePrice
) {
}
