package com.fy20047.tireordering.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fy20047.tireordering.backend.entity.Tire;
import com.fy20047.tireordering.backend.repository.TireRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // 啟動 Mockito
class TireServiceTest {

    @Mock // 建立假的資料庫
    private TireRepository tireRepository;

    @InjectMocks // 把假資料庫指派給真正的 TireService
    private TireService tireService;

    @Test
    // 驗證取得上架輪胎的功能，是否真的有去呼叫資料庫
    void getActiveTires_shouldUseRepository() {
        List<Tire> tires = List.of(); // 準備一個空的清單 List.of() 當作預期回傳值
        // 告訴資料庫 (tireRepository) 等等如果有人跟你要上架中 (Active) 的輪胎，就回傳這個空清單
        when(tireRepository.findActiveTires()).thenReturn(tires);

        List<Tire> result = tireService.getActiveTires();

        assertEquals(tires, result); // 確認拿到的結果跟資料庫給的一樣
        verify(tireRepository).findActiveTires(); // 確認 Service 真的有去呼叫 findActiveTires()，而不是跑去呼叫 findAll() (查全部) 或其他方法
    }

    @Test
    // 驗證搜尋功能是否有把使用者輸入的空白鍵清乾淨 (normalize)
    void searchTires_shouldNormalizeInputs() {
        when(tireRepository.search(any(), any(), any(), any())).thenReturn(List.of()); // 不管收到什麼搜尋條件 (any())，都回傳空清單就好 (因為不care回傳結果，只care傳進去的參數)

        tireService.searchTires("  Bridgestone ", "   ", " 205/55R16 ", true);
        // return tireRepository.search(normalize(brand), normalize(series), normalize(size), active);

        verify(tireRepository).search("Bridgestone", null, "205/55R16", true);
    }

    @Test
    // 驗證「更新輪胎」時，是否有正確地把新資料複製到舊物件上，而不是隨便亂存
    void updateTire_shouldUpdateFields() {
        Tire existing = Tire.builder()
                .brand("Old")
                .series("OldSeries")
                .origin("JP")
                .size("195/65R15")
                .price(1000)
                .isActive(true)
                .build();
        existing.setId(1L);

        Tire updated = Tire.builder()
                .brand("NewBrand")
                .series("NewSeries")
                .origin("TW")
                .size("205/55R16")
                .price(2000)
                .isActive(false)
                .build();

        // 有人查 ID=1，就把 existing (舊輪胎) 交給他
        when(tireRepository.findById(1L)).thenReturn(Optional.of(existing));
        // 有人叫你存檔 (save)，就假裝存好了
        when(tireRepository.save(any(Tire.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tire result = tireService.updateTire(1L, updated);

        ArgumentCaptor<Tire> captor = ArgumentCaptor.forClass(Tire.class);

        verify(tireRepository).save(captor.capture()); // 攔截 Service 最後要存進資料庫的那個物件
        Tire saved = captor.getValue(); // 把攔截到的物件拿出來檢查

        assertEquals("NewBrand", saved.getBrand());
        assertEquals("NewSeries", saved.getSeries());
        assertEquals("TW", saved.getOrigin());
        assertEquals("205/55R16", saved.getSize());
        assertEquals(2000, saved.getPrice());
        assertEquals(false, saved.isActive());
        assertEquals(saved, result);
    }

    @Test
    // 驗證快速上下架功能，只修改狀態欄位
    void updateActiveStatus_shouldPersistChange() {
        Tire existing = Tire.builder()
                .brand("Brand")
                .series("Series")
                .size("205/55R16")
                .isActive(true)
                .build();
        existing.setId(1L);

        when(tireRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tireRepository.save(any(Tire.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tire result = tireService.updateActiveStatus(1L, false);

        assertEquals(false, result.isActive()); // 檢查結果
        verify(tireRepository).save(existing); // 檢查行為（是否有執行存檔動作）
    }

    @Test
    // 驗證防呆機制，查不到輪胎要報錯
    void getTireById_whenNotFound_shouldThrow() {
        when(tireRepository.findById(99L)).thenReturn(Optional.empty()); // 有人查 ID=99，跟他說找不到

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> tireService.getTireById(99L)
        );
        assertEquals("Tire not found", ex.getMessage());
    }
}
