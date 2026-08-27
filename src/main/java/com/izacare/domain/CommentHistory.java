package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 일보 댓글 수정 이력 — 수정 직전의 내용을 그대로 보관 (일보 본문의 ReportHistory와 같은 방식) */
@Entity
public class CommentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ReportComment comment;

    @Lob
    private String previousContent;

    private String editedBy;

    private LocalDateTime editedAt = LocalDateTime.now();

    protected CommentHistory() {}

    public CommentHistory(ReportComment comment, String previousContent, String editedBy) {
        this.comment = comment;
        this.previousContent = previousContent;
        this.editedBy = editedBy;
    }

    public Long getId() { return id; }
    public ReportComment getComment() { return comment; }
    public String getPreviousContent() { return previousContent; }
    public String getEditedBy() { return editedBy; }
    public LocalDateTime getEditedAt() { return editedAt; }
}
