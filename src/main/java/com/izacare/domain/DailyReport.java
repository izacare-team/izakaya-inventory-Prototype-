package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일보 (예: 김가현 8/20) */
@Entity
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private LocalDate reportDate;

    @Lob
    private String content;

    /** 수정된 적이 있으면 시각이 들어감 → 화면에 "수정됨" 표시 */
    private LocalDateTime editedAt;

    protected DailyReport() {}

    public DailyReport(Long storeId, String author, LocalDate reportDate, String content) {
        this.storeId = storeId;
        this.author = author;
        this.reportDate = reportDate;
        this.content = content;
    }

    /** 수정 — 이전 값 보존은 ReportHistory에서 처리 */
    public void edit(LocalDate reportDate, String content) {
        this.reportDate = reportDate;
        this.content = content;
        this.editedAt = LocalDateTime.now();
    }

    public boolean isEdited() { return editedAt != null; }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getAuthor() { return author; }
    public LocalDate getReportDate() { return reportDate; }
    public String getContent() { return content; }
    public LocalDateTime getEditedAt() { return editedAt; }
}
