package com.fy20047.tireordering.tireservice.controller;

import com.fy20047.tireordering.tireservice.dto.TireListResponse;
import com.fy20047.tireordering.tireservice.dto.TireResponse;
import com.fy20047.tireordering.tireservice.entity.Tire;
import com.fy20047.tireordering.tireservice.service.TireService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 這個檔案用途：
// 提供公開輪胎查詢 API（/api/tires），將 Entity 轉為前端使用的 DTO。
@RestController
@RequestMapping("/api/tires")
public class TireController {

    // 這段欄位用途：呼叫輪胎業務邏輯層取得資料。
    private final TireService tireService;

    public TireController(TireService tireService) {
        this.tireService = tireService;
    }

    // 這段 API 用途：查詢輪胎列表，預設只回傳上架商品。
    @GetMapping
    public TireListResponse getTires(@RequestParam(name = "active", defaultValue = "true") boolean active) {
        List<Tire> tires = active ? tireService.getActiveTires() : tireService.getAllTires();
        List<TireResponse> items = tires.stream()
                .map(this::toResponse)
                .toList();
        return new TireListResponse(items);
    }

    // 這段 API 用途：依 ID 查詢單一輪胎資料。
    @GetMapping("/{id}")
    public TireResponse getTire(@PathVariable("id") Long id) {
        Tire tire = tireService.getTireById(id);
        return toResponse(tire);
    }

    // 這段方法用途：集中處理 Entity -> DTO 轉換，避免重複程式碼。
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
