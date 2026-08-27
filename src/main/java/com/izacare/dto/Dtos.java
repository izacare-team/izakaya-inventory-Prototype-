package com.izacare.dto;

import com.izacare.domain.AuditLine;
import com.izacare.domain.FoodItem;
import com.izacare.domain.StockAudit;
import com.izacare.domain.StockTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class Dtos {

    // ---------- 요청 ----------

    public record CreateItemRequest(
            @NotBlank String name,
            String category,
            String unit,
            @Min(0) int quantity,
            @Min(0) int minQuantity) {}

    /**
     * 입고 요청.
     * - 기존 품목: itemId 지정
     * - 신규 품목: itemId 없이 newItemName(+분류/단위/최소수량) 지정 → 등록과 동시에 입고
     */
    public record InboundRequest(
            Long itemId,
            String newItemName,
            String category,
            String unit,
            @Min(0) int minQuantity,
            @Min(1) int quantity,
            String note) {}

    public record DisposeRequest(
            @NotNull Long itemId,
            @Min(1) int quantity,
            @NotBlank String reason) {}

    /** 실사 라인 수량 수동 보정 */
    public record OverrideLineRequest(@Min(0) int finalQuantity) {}

    /**
     * 재고 목록에서 품목을 직접 고칠 때.
     * quantity는 변동량이 아니라 "고친 뒤 수량"이며, 둘 다 선택 항목이라 보낸 값만 반영된다.
     */
    public record AdjustQuantityRequest(
            @Min(0) Integer quantity,
            @Min(0) Integer minQuantity,
            String note) {}

    // ---------- 응답 ----------

    public record ItemResponse(
            Long id, String name, String category, String unit,
            int quantity, int minQuantity, boolean lowStock, LocalDateTime lastAuditedAt) {

        public static ItemResponse from(FoodItem i) {
            return new ItemResponse(i.getId(), i.getName(), i.getCategory(), i.getUnit(),
                    i.getQuantity(), i.getMinQuantity(), i.isLowStock(), i.getLastAuditedAt());
        }
    }

    public record TransactionResponse(
            Long id, Long itemId, String itemName, String type,
            int quantityChange, int quantityAfter, String note, LocalDateTime createdAt) {

        public static TransactionResponse from(StockTransaction t) {
            return new TransactionResponse(t.getId(), t.getItem().getId(), t.getItem().getName(),
                    t.getType().name(), t.getQuantityChange(), t.getQuantityAfter(),
                    t.getNote(), t.getCreatedAt());
        }
    }

    public record AuditLineResponse(
            Long id, Long itemId, String itemName, String recognizedName,
            int systemQuantity, int recognizedQuantity, int finalQuantity,
            int difference, double confidence, boolean unregistered) {

        public static AuditLineResponse from(AuditLine l) {
            FoodItem item = l.getItem();
            return new AuditLineResponse(
                    l.getId(),
                    item != null ? item.getId() : null,
                    item != null ? item.getName() : null,
                    l.getRecognizedName(),
                    l.getSystemQuantity(),
                    l.getRecognizedQuantity(),
                    l.getFinalQuantity(),
                    l.difference(),
                    l.getConfidence(),
                    item == null);
        }
    }

    public record AuditResponse(
            Long id, String status, String source,
            LocalDateTime createdAt, LocalDateTime confirmedAt,
            List<AuditLineResponse> lines) {

        public static AuditResponse from(StockAudit a) {
            return new AuditResponse(a.getId(), a.getStatus().name(), a.getSource(),
                    a.getCreatedAt(), a.getConfirmedAt(),
                    a.getLines().stream().map(AuditLineResponse::from).toList());
        }
    }
}
