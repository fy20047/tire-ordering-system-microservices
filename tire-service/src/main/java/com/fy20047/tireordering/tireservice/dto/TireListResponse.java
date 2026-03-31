package com.fy20047.tireordering.tireservice.dto;

import java.util.List;

// 這個檔案用途：
// 定義輪胎列表 API 回應格式，用 `items` 包裝多筆 `TireResponse`。
public record TireListResponse(List<TireResponse> items) {
}
