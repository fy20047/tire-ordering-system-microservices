package com.fy20047.tireordering.orderservice.enums;

// 這個檔案用途：
// 定義訂單生命週期狀態，供後台查單與狀態流轉使用。
public enum OrderStatus {
    // 這段列舉用途：新建立、待處理。
    PENDING,
    // 這段列舉用途：已確認接單。
    CONFIRMED,
    // 這段列舉用途：流程完成。
    COMPLETED,
    // 這段列舉用途：取消結案。
    CANCELLED
}
