package com.fy20047.tireordering.tireservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 這個檔案用途：
// 定義後台輪胎新增/編輯的請求格式，並集中欄位驗證規則。
public record AdminTireRequest(
        @NotBlank @Size(max = 100) String brand,
        @NotBlank @Size(max = 100) String series,
        @Size(max = 50) String origin,
        @NotBlank @Size(max = 50) String size,
        @NotNull @Min(0) Integer price,
        @NotNull Boolean isActive
) {
}
