package com.fy20047.tireordering.tireservice.service;

import com.fy20047.tireordering.tireservice.entity.Tire;
import com.fy20047.tireordering.tireservice.repository.TireRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 這個檔案用途：
// 封裝輪胎業務邏輯，提供前台查詢與後台維護流程共用的方法。
@Service
@Transactional(readOnly = true)
public class TireService {

    // 這段欄位用途：透過 Repository 與資料庫互動。
    private final TireRepository tireRepository;

    public TireService(TireRepository tireRepository) {
        this.tireRepository = tireRepository;
    }

    // 這段方法用途：提供前台僅查上架輪胎。
    public List<Tire> getActiveTires() {
        return tireRepository.findActiveTires();
    }

    // 這段方法用途：提供後台查看全部輪胎。
    public List<Tire> getAllTires() {
        return tireRepository.findAll();
    }

    // 這段方法用途：提供後台條件查詢，並先做輸入字串清洗。
    public List<Tire> searchTires(String brand, String series, String size, Boolean active) {
        return tireRepository.search(normalize(brand), normalize(series), normalize(size), active);
    }

    // 這段方法用途：新增輪胎資料。
    @Transactional
    public Tire createTire(Tire tire) {
        return tireRepository.save(tire);
    }

    // 這段方法用途：更新既有輪胎內容。
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

    // 這段方法用途：僅調整上下架狀態，避免更新整包資料。
    @Transactional
    public Tire updateActiveStatus(Long id, boolean isActive) {
        Tire existing = getTireById(id);
        existing.setActive(isActive);
        return tireRepository.save(existing);
    }

    // 這段方法用途：依 ID 取得輪胎，不存在時拋出一致的錯誤訊息。
    public Tire getTireById(Long tireId) {
        return tireRepository.findById(tireId)
                .orElseThrow(() -> new IllegalArgumentException("Tire not found"));
    }

    // 這段方法用途：將空白字串正規化為 null，避免查詢條件判斷混亂。
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
