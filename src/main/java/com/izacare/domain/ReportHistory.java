package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일보 수정 이력 — 수정 직전의 내용을 그대로 보관 */
@Entity
public class ReportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DailyReport report;

    @Lob
    private String previousContent;

    private LocalDate previousReportDate;

    private String editedBy;

    private LocalDateTime editedAt = LocalDateTime.now();

    protected ReportHistory() {}

    public ReportHistory(DailyReport report, String previousContent,
                         LocalDate previousReportDate, String editedBy) {
        this.report = report;
        this.previousContent = previousContent;
        this.previousReportDate = previousReportDate;
        this.editedBy = editedBy;
    }

    public Long getId() { return id; }
    public DailyReport getReport() { return report; }
    public String getPreviousContent() { return previousContent; }
    public LocalDate getPreviousReportDate() { return previousReportDate; }
    public String getEditedBy() { return editedBy; }
    public LocalDateTime getEditedAt() { return editedAt; }
}
