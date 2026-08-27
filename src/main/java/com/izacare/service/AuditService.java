package com.izacare.service;

import com.izacare.domain.AuditLine;
import com.izacare.domain.FoodItem;
import com.izacare.domain.StockAudit;
import com.izacare.domain.StockTransaction;
import com.izacare.dto.Dtos.AuditResponse;
import com.izacare.repository.FoodItemRepository;
import com.izacare.repository.StockAuditRepository;
import com.izacare.repository.StockTransactionRepository;
import com.izacare.vision.RecognizedItem;
import com.izacare.vision.VisionAiClient;
import com.izacare.vision.VisionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 실사 핵심 흐름:
 *  1) 사진 업로드 → Vision AI 인식 → DRAFT 실사 세션 생성 (시스템 수량과 자동 대조)
 *  2) 사용자가 라인별 검토, 오인식 수량 수정
 *  3) 확정 → 차이나는 품목만 재고 조정 + AUDIT_ADJUST 이력 기록
 */
@Service
@Transactional
public class AuditService {

    private final VisionAiClient visionAiClient;
    private final FoodItemRepository itemRepository;
    private final StockAuditRepository auditRepository;
    private final StockTransactionRepository transactionRepository;
    private final double confidenceThreshold;

    public AuditService(VisionAiClient visionAiClient,
                        FoodItemRepository itemRepository,
                        StockAuditRepository auditRepository,
                        StockTransactionRepository transactionRepository,
                        @Value("${vision.confidence-threshold:0.5}") double confidenceThreshold) {
        this.visionAiClient = visionAiClient;
        this.itemRepository = itemRepository;
        this.auditRepository = auditRepository;
        this.transactionRepository = transactionRepository;
        this.confidenceThreshold = confidenceThreshold;
    }

    /** 1단계: 사진 → AI 인식 → DRAFT 실사 생성 */
    public AuditResponse createAuditFromImage(Long storeId, byte[] imageBytes, String contentType) {
        List<FoodItem> allItems = itemRepository.findByStoreId(storeId);
        List<String> knownNames = allItems.stream().map(FoodItem::getName).toList();

        VisionResult result = visionAiClient.recognize(imageBytes, contentType, knownNames);

        StockAudit audit = new StockAudit(storeId, "AI_VISION", result.rawResponse());

        for (RecognizedItem recognized : result.items()) {
            if (recognized.confidence() < confidenceThreshold) continue; // 신뢰도 낮으면 제외

            FoodItem matched = matchByName(allItems, recognized.name());
            int systemQty = matched != null ? matched.getQuantity() : 0;

            audit.addLine(new AuditLine(matched, recognized.name(),
                    systemQty, recognized.quantity(), recognized.confidence()));
        }

        if (audit.getLines().isEmpty()) {
            throw new IllegalStateException("사진에서 인식된 품목이 없습니다. 다시 촬영해 주세요.");
        }
        return AuditResponse.from(auditRepository.save(audit));
    }

    /** 2단계: 라인 수량 수동 보정 */
    public AuditResponse overrideLine(Long storeId, Long auditId, Long lineId, int finalQuantity) {
        StockAudit audit = getDraftAudit(storeId, auditId);
        AuditLine line = audit.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("실사 라인을 찾을 수 없습니다: " + lineId));
        line.overrideFinalQuantity(finalQuantity);
        return AuditResponse.from(audit);
    }

    /**
     * 3단계: 확정 — 차이나는 품목만 실물 수량으로 조정하고 이력을 남긴다.
     * AI가 인식했지만 등록되지 않은 품목은 이 시점에 자동 등록된다.
     */
    public AuditResponse confirm(Long storeId, Long auditId) {
        StockAudit audit = getDraftAudit(storeId, auditId);

        for (AuditLine line : audit.getLines()) {
            FoodItem item = line.getItem();
            boolean autoRegistered = false;

            if (item == null) {
                // 미등록 품목 자동 등록 (같은 이름이 그새 생겼으면 그 품목 사용)
                String name = line.getRecognizedName().trim();
                item = itemRepository.findByStoreIdAndName(storeId, name).orElse(null);
                if (item == null) {
                    item = itemRepository.save(new FoodItem(storeId, name, "미분류", "개", 0, 0));
                    autoRegistered = true;
                }
                line.attachItem(item);
            }

            int diff = line.getFinalQuantity() - item.getQuantity();
            if (diff == 0 && !autoRegistered) {
                item.adjustTo(item.getQuantity()); // 차이 없어도 실사 시각은 갱신
                continue;
            }
            item.adjustTo(line.getFinalQuantity());
            String prefix = autoRegistered ? "[실사 자동 등록] " : "";
            transactionRepository.save(new StockTransaction(item,
                    StockTransaction.Type.AUDIT_ADJUST, diff, item.getQuantity(),
                    prefix + "실사 #" + audit.getId() + " 조정 (AI 인식 " + line.getRecognizedQuantity()
                            + " → 최종 " + line.getFinalQuantity() + ")"));
        }

        audit.confirm();
        return AuditResponse.from(audit);
    }

    public AuditResponse cancel(Long storeId, Long auditId) {
        StockAudit audit = getDraftAudit(storeId, auditId);
        audit.cancel();
        return AuditResponse.from(audit);
    }

    @Transactional(readOnly = true)
    public AuditResponse findAudit(Long storeId, Long id) {
        StockAudit audit = auditRepository.findWithLinesById(id)
                .orElseThrow(() -> new IllegalArgumentException("실사를 찾을 수 없습니다: " + id));
        if (!audit.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("실사를 찾을 수 없습니다: " + id);
        }
        return AuditResponse.from(audit);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> recentAudits(Long storeId) {
        return auditRepository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId)
                .stream().map(AuditResponse::from).toList();
    }

    private StockAudit getDraftAudit(Long storeId, Long id) {
        StockAudit audit = auditRepository.findWithLinesById(id)
                .orElseThrow(() -> new IllegalArgumentException("실사를 찾을 수 없습니다: " + id));
        if (!audit.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("실사를 찾을 수 없습니다: " + id);
        }
        if (audit.getStatus() != StockAudit.Status.DRAFT) {
            throw new IllegalStateException("이미 처리된 실사입니다: " + audit.getStatus());
        }
        return audit;
    }

    /** 품목명 매칭: 완전 일치 → 공백 제거 일치 → 포함 관계 순으로 시도 */
    private FoodItem matchByName(List<FoodItem> items, String recognizedName) {
        String normalized = recognizedName.replaceAll("\\s", "");
        return items.stream()
                .filter(i -> i.getName().equals(recognizedName))
                .findFirst()
                .or(() -> items.stream()
                        .filter(i -> i.getName().replaceAll("\\s", "").equalsIgnoreCase(normalized))
                        .findFirst())
                .or(() -> items.stream()
                        .filter(i -> i.getName().contains(recognizedName)
                                || recognizedName.contains(i.getName()))
                        .findFirst())
                .orElse(null);
    }
}
