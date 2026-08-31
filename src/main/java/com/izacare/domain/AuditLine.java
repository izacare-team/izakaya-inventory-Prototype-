package com.izacare.domain;

import jakarta.persistence.*;

/**
 * 실사 세션의 한 줄 = 품목 하나에 대한 (시스템 수량 vs 인식/실측 수량) 비교.
 */
@Entity
public class AuditLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StockAudit audit;

    /** 매칭된 품목. AI가 인식했지만 등록 안 된 품목이면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    private FoodItem item;

    /** AI가 인식한 품목명 원본 (매칭 실패 대비 보존) */
    private String recognizedName;

    /** 실사 시점의 시스템상 수량 */
    private int systemQuantity;

    /** AI가 인식(카운팅)한 수량 — 제안값 */
    private int recognizedQuantity;

    /** 사용자가 검토 후 최종 확정한 수량 (초기값 = recognizedQuantity) */
    private int finalQuantity;

    /** AI 인식 신뢰도 0.0 ~ 1.0 */
    private double confidence;

    protected AuditLine() {}

    public AuditLine(FoodItem item, String recognizedName, int systemQuantity,
                     int recognizedQuantity, double confidence) {
        this.item = item;
        this.recognizedName = recognizedName;
        this.systemQuantity = systemQuantity;
        this.recognizedQuantity = recognizedQuantity;
        this.finalQuantity = recognizedQuantity;
        this.confidence = confidence;
    }

    void setAudit(StockAudit audit) { this.audit = audit; }

    /** 확정 시 미등록 품목이 자동 등록되면 새 품목을 연결 */
    public void attachItem(FoodItem item) { this.item = item; }

    /**
     * 같은 품목이 다른 사진에서 또 인식됐을 때 합친다 (사진 여러 장을 한 실사로 묶는 경우).
     * 신뢰도는 둘 중 낮은 쪽을 남긴다 — 한 장이라도 흐릿했으면 그 줄 전체를 의심해야 하므로.
     */
    public void mergeRecognized(int quantity, double confidence) {
        this.recognizedQuantity += quantity;
        this.finalQuantity = this.recognizedQuantity;
        this.confidence = Math.min(this.confidence, confidence);
    }

    /** 사용자가 오인식을 바로잡을 때 */
    public void overrideFinalQuantity(int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("수량은 0 이상이어야 합니다.");
        this.finalQuantity = quantity;
    }

    /** 시스템 수량과 최종 수량의 차이 (실물 - 장부) */
    public int difference() {
        return finalQuantity - systemQuantity;
    }

    public Long getId() { return id; }
    public StockAudit getAudit() { return audit; }
    public FoodItem getItem() { return item; }
    public String getRecognizedName() { return recognizedName; }
    public int getSystemQuantity() { return systemQuantity; }
    public int getRecognizedQuantity() { return recognizedQuantity; }
    public int getFinalQuantity() { return finalQuantity; }
    public double getConfidence() { return confidence; }
}
