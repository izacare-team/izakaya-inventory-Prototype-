package com.izacare.service;

import com.izacare.domain.FoodItem;
import com.izacare.domain.StockAudit;
import com.izacare.dto.Dtos.AuditResponse;
import com.izacare.repository.FoodItemRepository;
import com.izacare.repository.StockAuditRepository;
import com.izacare.repository.StockTransactionRepository;
import com.izacare.vision.RecognizedItem;
import com.izacare.vision.VisionAiClient;
import com.izacare.vision.VisionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 사진 여러 장을 한 실사로 묶을 때 같은 품목이 중복으로 올라오는지 확인.
 * 중복이 남으면 확정 단계에서 같은 품목을 두 번 조정해 재고가 틀어진다.
 */
class AuditServiceTest {

    private AuditService service(Path imageDir, List<RecognizedItem> recognized, List<FoodItem> items) {
        VisionAiClient vision = mock(VisionAiClient.class);
        when(vision.recognize(any(), any(), any())).thenReturn(new VisionResult(recognized, "{}"));

        FoodItemRepository itemRepo = mock(FoodItemRepository.class);
        when(itemRepo.findByStoreId(1L)).thenReturn(items);

        StockAuditRepository auditRepo = mock(StockAuditRepository.class);
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0, StockAudit.class));

        return new AuditService(vision, itemRepo, auditRepo,
                mock(StockTransactionRepository.class), 0.5, imageDir.toString());
    }

    @Test
    void 여러_사진에서_같은_품목이_나오면_한_줄로_합쳐진다(@TempDir Path dir) {
        FoodItem beer = new FoodItem(1L, "생맥주", "주류", "병", 10, 3);

        AuditResponse res = service(dir, List.of(
                new RecognizedItem("생맥주", 3, 0.9),    // 냉장고 사진
                new RecognizedItem("생 맥주", 2, 0.7),   // 주류고 사진 — 공백만 다름
                new RecognizedItem("레몬", 4, 0.8)       // 미등록 품목
        ), List.of(beer)).createAuditFromImages(1L, List.of(new byte[]{1}, new byte[]{2}), "image/jpeg");

        assertThat(res.lines()).hasSize(2);

        var beerLine = res.lines().stream().filter(l -> "생맥주".equals(l.itemName())).findFirst().orElseThrow();
        assertThat(beerLine.recognizedQuantity()).isEqualTo(5);   // 3 + 2 합산
        assertThat(beerLine.finalQuantity()).isEqualTo(5);
        assertThat(beerLine.confidence()).isEqualTo(0.7);         // 낮은 쪽 유지
        assertThat(beerLine.difference()).isEqualTo(-5);          // 장부 10 - 실물 5
    }

    @Test
    void 신뢰도가_낮은_인식은_제외된다(@TempDir Path dir) {
        AuditResponse res = service(dir, List.of(
                new RecognizedItem("연어", 2, 0.9),
                new RecognizedItem("정체불명", 1, 0.3)
        ), List.of()).createAuditFromImages(1L, List.of(new byte[]{1}), "image/jpeg");

        assertThat(res.lines()).singleElement()
                .satisfies(l -> assertThat(l.recognizedName()).isEqualTo("연어"));
    }

    @Test
    void 실사_사진은_디스크에_남는다(@TempDir Path dir) throws Exception {
        AuditResponse res = service(dir, List.of(new RecognizedItem("연어", 2, 0.9)), List.of())
                .createAuditFromImages(1L, List.of(new byte[]{1, 2, 3}, new byte[]{4}), "image/jpeg");

        assertThat(res.imageCount()).isEqualTo(2);
        try (var files = Files.list(dir)) {
            assertThat(files).hasSize(2);
        }
    }
}
