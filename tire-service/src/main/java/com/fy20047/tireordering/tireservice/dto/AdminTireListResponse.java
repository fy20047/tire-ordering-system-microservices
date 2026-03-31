package com.fy20047.tireordering.tireservice.dto;

import java.util.List;

// 這個檔案用途：
// 定義後台輪胎列表回應格式，用 `items` 包裝多筆管理資料。
public record AdminTireListResponse(List<AdminTireResponse> items) {
}
