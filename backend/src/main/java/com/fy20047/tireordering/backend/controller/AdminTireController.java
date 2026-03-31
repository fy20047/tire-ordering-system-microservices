package com.fy20047.tireordering.backend.controller;

import com.fy20047.tireordering.backend.dto.AdminTireListResponse;
import com.fy20047.tireordering.backend.dto.AdminTireRequest;
import com.fy20047.tireordering.backend.dto.AdminTireResponse;
import com.fy20047.tireordering.backend.dto.UpdateTireStatusRequest;
import com.fy20047.tireordering.backend.entity.Tire;
import com.fy20047.tireordering.backend.service.TireService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 後台輪胎 CRUD API（搜尋/新增/編輯/上下架）
@RestController
@RequestMapping("/api/admin/tires")
@ConditionalOnProperty(
        name = "feature.backend-tire-endpoints-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdminTireController {

    private final TireService tireService;

    public AdminTireController(TireService tireService) {
        this.tireService = tireService;
    }

    @GetMapping
    public AdminTireListResponse list(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) Boolean active
    ) {
        List<Tire> tires = tireService.searchTires(brand, series, size, active);
        List<AdminTireResponse> items = tires.stream().map(this::toResponse).collect(Collectors.toList());
        return new AdminTireListResponse(items);
    }

    @PostMapping
    public ResponseEntity<AdminTireResponse> create(@Valid @RequestBody AdminTireRequest request) {
        Tire created = tireService.createTire(toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    public AdminTireResponse update(@PathVariable Long id, @Valid @RequestBody AdminTireRequest request) {
        Tire updated = tireService.updateTire(id, toEntity(request));
        return toResponse(updated);
    }

    @PatchMapping("/{id}/active")
    public AdminTireResponse updateActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTireStatusRequest request
    ) {
        Tire updated = tireService.updateActiveStatus(id, request.isActive());
        return toResponse(updated);
    }

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
