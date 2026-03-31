package com.fy20047.tireordering.backend.controller;

import com.fy20047.tireordering.backend.dto.TireListResponse;
import com.fy20047.tireordering.backend.dto.TireResponse;
import com.fy20047.tireordering.backend.entity.Tire;
import com.fy20047.tireordering.backend.service.TireService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tires")
@ConditionalOnProperty(
        name = "feature.backend-tire-endpoints-enabled",
        havingValue = "true",
        matchIfMissing = false
)
// 處理資料顯示與轉換（Get），資料庫 -> Service -> 輸出 DTO
// 把資料庫裡的 Tire (Entity) 轉成前端能看的 TireResponse (DTO)
// 預設 200 OK
public class TireController {

    private final TireService tireService;

    public TireController(TireService tireService) {
        this.tireService = tireService;
    }

    // 查詢請求
    @GetMapping
    public TireListResponse getTires(@RequestParam(name = "active", defaultValue = "true") boolean active) {
        List<Tire> tires = active ? tireService.getActiveTires() : tireService.getAllTires();
        List<TireResponse> items = tires.stream() // 1. 把清單變成輸送帶
                .map(this::toResponse) // 2. 對每個輪胎執行 toResponse 方法 (轉換)
                .collect(Collectors.toList());  // 3. 把處理好的東西裝回 List
        return new TireListResponse(items);
    }

    // 查單一顆輪胎
    // @RequestParam 是抓 ? 後面的參數 (篩選條件)
    // @PathVariable 是抓 / 中間的路徑 (指定資源 ID)
    @GetMapping("/{id}")
    public TireResponse getTire(@PathVariable("id") Long id) {
        Tire tire = tireService.getTireById(id);
        return toResponse(tire);
    }

    // 在 getTires (查列表) 和 getTire (查單個) 這兩個地方，都需要把 Tire (資料庫格式) 轉成 TireResponse (前端格式)
    // DRY 原則 - Don't Repeat Yourself，把它抽出來變成一個小方法，程式碼更乾淨，以後如果要改格式，改這個小方法就好
    private TireResponse toResponse(Tire tire) {
        return new TireResponse(
                tire.getId(),
                tire.getBrand(),
                tire.getSeries(),
                tire.getOrigin(),
                tire.getSize(),
                tire.getPrice(),
                tire.isActive()
        );
    }
}
