package com.fy20047.tireordering.orderservice.repository;

import com.fy20047.tireordering.orderservice.entity.Tire;
import org.springframework.data.jpa.repository.JpaRepository;

// 這個檔案用途：
// 過渡期提供訂單流程查詢輪胎主檔的資料存取介面。
public interface TireRepository extends JpaRepository<Tire, Long> {
}
