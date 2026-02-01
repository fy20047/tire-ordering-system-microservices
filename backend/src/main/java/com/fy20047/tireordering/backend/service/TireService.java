package com.fy20047.tireordering.backend.service;

import com.fy20047.tireordering.backend.entity.Tire;
import com.fy20047.tireordering.backend.repository.TireRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TireService {

    private final TireRepository tireRepository;

    public TireService(TireRepository tireRepository) {
        this.tireRepository = tireRepository;
    }

    public List<Tire> getActiveTires() {
        return tireRepository.findActiveTires();
    }

    public List<Tire> getAllTires() {
        return tireRepository.findAll();
    }

    public List<Tire> searchTires(String brand, String series, String size, Boolean active) {
        return tireRepository.search(normalize(brand), normalize(series), normalize(size), active);
    }

    // 將新的輪胎物件存入資料庫
    @Transactional
    public Tire createTire(Tire tire) {
        return tireRepository.save(tire);
    }

    // 根據 ID 撈出舊資料，然後逐一更新欄位
    @Transactional
    public Tire updateTire(Long id, Tire updated) {
        Tire existing = getTireById(id);
        existing.setBrand(updated.getBrand());
        existing.setSeries(updated.getSeries());
        existing.setOrigin(updated.getOrigin());
        existing.setSize(updated.getSize());
        existing.setPrice(updated.getPrice());
        existing.setActive(updated.isActive());
        return tireRepository.save(existing);
    }

    // 快速切換輪胎的上下架狀態，不需要更新整個物件
    @Transactional
    public Tire updateActiveStatus(Long id, boolean isActive) {
        Tire existing = getTireById(id);
        existing.setActive(isActive);
        return tireRepository.save(existing);
    }

    public Tire getTireById(Long tireId) {
        return tireRepository.findById(tireId)
                .orElseThrow(() -> new IllegalArgumentException("Tire not found"));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
