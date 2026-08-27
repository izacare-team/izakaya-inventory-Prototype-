package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 실사(재고 조사) 세션.
 * 사진 촬영 → AI 인식 → 사용자가 검토/수정 → 확정(재고 반영) 까지의 단위.
 */
@Entity
public class StockAudit {

    public enum Status { DRAFT, CONFIRMED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    /** 어떤 방식으로 만들어졌나 (AI_VISION / MANUAL) */
    private String source;

    /** AI 응답 원본 (디버깅/재검증용) */
    @Lob
    private String rawAiResponse;

    @OneToMany(mappedBy = "audit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditLine> lines = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime confirmedAt;

    protected StockAudit() {}

    public StockAudit(Long storeId, String source, String rawAiResponse) {
        this.storeId = storeId;
        this.source = source;
        this.rawAiResponse = rawAiResponse;
    }

    public void addLine(AuditLine line) {
        lines.add(line);
        line.setAudit(this);
    }

    public void confirm() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("이미 처리된 실사입니다: " + status);
        }
        this.status = Status.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("이미 처리된 실사입니다: " + status);
        }
        this.status = Status.CANCELLED;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Status getStatus() { return status; }
    public String getSource() { return source; }
    public String getRawAiResponse() { return rawAiResponse; }
    public List<AuditLine> getLines() { return lines; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}
