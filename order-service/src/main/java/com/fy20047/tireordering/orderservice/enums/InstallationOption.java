package com.fy20047.tireordering.orderservice.enums;

// 這個檔案用途：
// 定義訂單安裝/取貨方式的列舉，供訂單資料模型與業務驗證共用。
public enum InstallationOption {
    // 這段列舉用途：到店安裝。
    INSTALL,
    // 這段列舉用途：到店取貨。
    PICKUP,
    // 這段列舉用途：住家配送。
    DELIVERY
}
