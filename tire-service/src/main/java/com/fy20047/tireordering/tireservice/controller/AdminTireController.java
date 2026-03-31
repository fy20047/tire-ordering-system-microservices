package com.fy20047.tireordering.tireservice.controller;

import com.fy20047.tireordering.tireservice.dto.AdminTireListResponse;
import com.fy20047.tireordering.tireservice.dto.AdminTireRequest;
import com.fy20047.tireordering.tireservice.dto.AdminTireResponse;
import com.fy20047.tireordering.tireservice.dto.UpdateTireStatusRequest;
import com.fy20047.tireordering.tireservice.entity.Tire;
import com.fy20047.tireordering.tireservice.service.TireService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 這個檔案用途：
// 提供後台輪胎管理 API（查詢、新增、編輯、上下架）。
@RestController
@RequestMapping("/api/admin/tires")
public class AdminTireController {

    // 這段欄位用途：呼叫輪胎業務邏輯層處理後台操作。
    private final TireService tireService;

    public AdminTireController(TireService tireService) {
        this.tireService = tireService;
    }

    // 這段 API 用途：後台條件查詢輪胎列表。
    @GetMapping
    public AdminTireListResponse list(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) Boolean active
    ) {
        List<Tire> tires = tireService.searchTires(brand, series, size, active);
        List<AdminTireResponse> items = tires.stream()
                .map(this::toResponse)
                .toList();
        return new AdminTireListResponse(items);
    }

    // 這段 API 用途：新增輪胎資料，成功回傳 201。
    @PostMapping
    public ResponseEntity<AdminTireResponse> create(@Valid @RequestBody AdminTireRequest request) {
        Tire created = tireService.createTire(toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    // 這段 API 用途：完整更新指定輪胎資料。
    @PutMapping("/{id}")
    public AdminTireResponse update(@PathVariable Long id, @Valid @RequestBody AdminTireRequest request) {
        Tire updated = tireService.updateTire(id, toEntity(request));
        return toResponse(updated);
    }

    // 這段 API 用途：僅更新輪胎上下架狀態。
    @PatchMapping("/{id}/active")
    public AdminTireResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTireStatusRequest request
    ) {
        Tire updated = tireService.updateActiveStatus(id, request.isActive());
        return toResponse(updated);
    }

    // 這段方法用途：把請求 DTO 轉為 Entity，並統一做字串 trim。
    private Tire toEntity(AdminTireRequest request) {
        return Tire.builder()
                .brand(request.brand().trim())
                .series(request.series().trim())
                .origin(request.origin() == null ? null : request.origin().trim())
                .size(request.size().trim())
                .price(request.price())
                .isActive(request.isActive())
                .build();
    }

    // 這段方法用途：把 Entity 轉為後台回應 DTO。
    private AdminTireResponse toResponse(Tire tire) {
        return new AdminTireResponse(
                tire.getId(),
                tire.getBrand(),
                tire.getSeries(),
                tire.getOrigin(),
                tire.getSize(),
                tire.getPrice(),
                tire.isActive(),
                tire.getCreatedAt(),
                tire.getUpdatedAt()
        );
    }
}
