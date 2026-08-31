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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final Path imageDir;

    public AuditService(VisionAiClient visionAiClient,
                        FoodItemRepository itemRepository,
                        StockAuditRepository auditRepository,
                        StockTransactionRepository transactionRepository,
                        @Value("${vision.confidence-threshold:0.5}") double confidenceThreshold,
                        @Value("${vision.image-dir:./data/audit-images}") String imageDir) {
        this.visionAiClient = visionAiClient;
        this.itemRepository = itemRepository;
        this.auditRepository = auditRepository;
        this.transactionRepository = transactionRepository;
        this.confidenceThreshold = confidenceThreshold;
        this.imageDir = Path.of(imageDir).toAbsolutePath().normalize();
    }

    /** 1단계: 사진(냉장고·주류고·창고 등 여러 장) → AI 인식 → DRAFT 실사 생성 */
    public AuditResponse createAuditFromImages(Long storeId, List<byte[]> images, String contentType) {
        List<FoodItem> allItems = itemRepository.findByStoreId(storeId);
        List<String> knownNames = allItems.stream().map(FoodItem::getName).toList();

        VisionResult result = visionAiClient.recognize(images, contentType, knownNames);

        StockAudit audit = new StockAudit(storeId, "AI_VISION", result.rawResponse());

        for (AuditLine line : toLines(allItems, result.items())) {
            audit.addLine(line);
        }

        if (audit.getLines().isEmpty()) {
            throw new IllegalStateException("사진에서 인식된 품목이 없습니다. 다시 촬영해 주세요.");
        }

        for (byte[] image : images) {
            audit.addImageFile(storeImage(image, contentType));
        }
        return AuditResponse.from(auditRepository.save(audit));
    }

    /**
     * 인식 결과 → 실사 라인.
     * 사진 여러 장을 한 번에 보내면 같은 품목이 두 번 올라올 수 있는데, 그대로 두면
     * 확정 시 같은 품목을 두 번 조정해 뒤 라인 값으로 덮어써진다 → 여기서 미리 합친다.
     */
    private List<AuditLine> toLines(List<FoodItem> allItems, List<RecognizedItem> recognizedItems) {
        Map<String, AuditLine> merged = new LinkedHashMap<>();

        for (RecognizedItem recognized : recognizedItems) {
            if (recognized.confidence() < confidenceThreshold) continue; // 신뢰도 낮으면 제외

            FoodItem matched = matchByName(allItems, recognized.name());
            // 등록 품목이면 품목 기준으로, 미등록이면 이름(공백 무시) 기준으로 중복을 판단
            String key = matched != null
                    ? "item:" + matched.getId()
                    : "name:" + recognized.name().replaceAll("\s", "").toLowerCase();

            AuditLine existing = merged.get(key);
            if (existing != null) {
                existing.mergeRecognized(recognized.quantity(), recognized.confidence());
                continue;
            }
            int systemQty = matched != null ? matched.getQuantity() : 0;
            merged.put(key, new AuditLine(matched, recognized.name(),
                    systemQty, recognized.quantity(), recognized.confidence()));
        }
        return new ArrayList<>(merged.values());
    }

    /** 실사 사진을 디스크에 저장하고 파일명을 돌려준다 (수량 조정의 근거 자료) */
    private String storeImage(byte[] image, String contentType) {
        String ext = contentType != null && contentType.contains("png") ? ".png" : ".jpg";
        String fileName = UUID.randomUUID() + ext;
        try {
            Files.createDirectories(imageDir);
            Files.write(imageDir.resolve(fileName), image);
        } catch (IOException e) {
            throw new UncheckedIOException("실사 사진 저장 실패: " + imageDir, e);
        }
        // ponytail: 로컬 디스크에 저장. 서버를 여러 대로 늘리거나 백업이 필요해지면 S3로.
        return fileName;
    }

    /** 실사 사진 원본 읽기 — 다른 가게의 실사는 볼 수 없다 */
    @Transactional(readOnly = true)
    public byte[] readImage(Long storeId, Long auditId, int index) {
        StockAudit audit = auditRepository.findById(auditId)
                .orElseThrow(() -> new IllegalArgumentException("실사를 찾을 수 없습니다: " + auditId));
        if (!audit.getStoreId().equals(storeId)) {
            throw new IllegalArgumentException("실사를 찾을 수 없습니다: " + auditId);
        }
        List<String> files = audit.getImageFiles();
        if (index < 0 || index >= files.size()) {
            throw new IllegalArgumentException("실사 사진을 찾을 수 없습니다: " + index);
        }
        Path file = imageDir.resolve(files.get(index)).normalize();
        if (!file.startsWith(imageDir)) {   // 저장 파일명은 UUID지만 경로 이탈은 한 번 더 막는다
            throw new IllegalArgumentException("실사 사진을 찾을 수 없습니다: " + index);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("실사 사진을 읽을 수 없습니다: " + file, e);
        }
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
