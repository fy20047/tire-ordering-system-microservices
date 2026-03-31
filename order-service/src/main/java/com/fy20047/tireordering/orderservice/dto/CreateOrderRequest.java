package com.fy20047.tireordering.orderservice.dto;

import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 這個檔案用途：
// 定義前台建單 API 的請求格式與欄位驗證規則。
public record CreateOrderRequest(
        // 這段欄位用途：指定購買的輪胎主檔編號。
        @NotNull Long tireId,
        // 這段欄位用途：購買數量，至少 1。
        @NotNull @Min(1) Integer quantity,
        // 這段欄位用途：顧客姓名。
        @NotBlank @Size(max = 100) String customerName,
        // 這段欄位用途：聯絡電話。
        @NotBlank @Size(max = 50) String phone,
        // 這段欄位用途：聯絡 Email（可為空，但若有值需符合格式）。
        @Email @Size(max = 255) String email,
        // 這段欄位用途：安裝/取貨方式。
        @NotNull InstallationOption installationOption,
        // 這段欄位用途：配送地址（DELIVERY 情境下會由服務層強制檢查）。
        @Size(max = 500) String deliveryAddress,
        // 這段欄位用途：車型資訊。
        @NotBlank @Size(max = 100) String carModel,
        // 這段欄位用途：補充備註。
        @Size(max = 1000) String notes
) {
}
