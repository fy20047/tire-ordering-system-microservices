package com.fy20047.tireordering.tireservice.repository;

import com.fy20047.tireordering.tireservice.entity.Tire;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 這個檔案用途：
// 提供 Tire Service 的輪胎資料存取方法（公開查詢與後台搜尋共用）。
public interface TireRepository extends JpaRepository<Tire, Long> {

    // 這段查詢用途：前台只看上架輪胎，並固定排序提升列表穩定性。
    @Query("select t from Tire t where t.isActive = true order by t.brand, t.series, t.size")
    List<Tire> findActiveTires();

    // 這段查詢用途：後台條件式搜尋（品牌/系列/尺寸/上下架）且支援空條件。
    @Query("""
            select t from Tire t
            where (:brand is null or lower(t.brand) like lower(concat('%', :brand, '%')))
              and (:series is null or lower(t.series) like lower(concat('%', :series, '%')))
              and (:size is null or lower(t.size) like lower(concat('%', :size, '%')))
              and (:active is null or t.isActive = :active)
            order by t.brand, t.series, t.size
            """)
    List<Tire> search(
            @Param("brand") String brand,
            @Param("series") String series,
            @Param("size") String size,
            @Param("active") Boolean active
    );
}
