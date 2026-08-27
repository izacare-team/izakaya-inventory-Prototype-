package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 공지 수정 이력 — 수정 직전의 내용을 그대로 보관 */
@Entity
public class NoticeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Notice notice;

    @Column(length = 2000)
    private String previousTitle;

    private LocalDate previousEventDate;

    private String editedBy;

    private LocalDateTime editedAt = LocalDateTime.now();

    protected NoticeHistory() {}

    public NoticeHistory(Notice notice, String previousTitle,
                         LocalDate previousEventDate, String editedBy) {
        this.notice = notice;
        this.previousTitle = previousTitle;
        this.previousEventDate = previousEventDate;
        this.editedBy = editedBy;
    }

    public Long getId() { return id; }
    public Notice getNotice() { return notice; }
    public String getPreviousTitle() { return previousTitle; }
    public LocalDate getPreviousEventDate() { return previousEventDate; }
    public String getEditedBy() { return editedBy; }
    public LocalDateTime getEditedAt() { return editedAt; }
}
