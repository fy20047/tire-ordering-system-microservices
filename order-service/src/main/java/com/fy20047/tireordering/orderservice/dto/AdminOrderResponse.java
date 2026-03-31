package com.fy20047.tireordering.orderservice.dto;

import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import java.time.LocalDateTime;

// 這個檔案用途：
// 定義後台單筆訂單回應格式，包含訂單欄位與訂單內保存的輪胎 snapshot 欄位。
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
        // 這段欄位用途：輪胎快照欄位（來源是訂單建立當下拷貝值，而非即時輪胎主檔）。
        Long tireId,
        String tireBrand,
        String tireSeries,
        String tireOrigin,
        String tireSize,
        Integer tirePrice
) {
}
