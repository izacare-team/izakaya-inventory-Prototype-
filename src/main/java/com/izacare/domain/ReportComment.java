package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 일보 댓글 */
@Entity
public class ReportComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private DailyReport report;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** 마지막으로 고친 시각 — null이면 한 번도 수정되지 않은 댓글 */
    private LocalDateTime editedAt;

    protected ReportComment() {}

    public ReportComment(DailyReport report, String author, String content) {
        this.report = report;
        this.author = author;
        this.content = content;
    }

    /** 내용 수정 — 이전 내용 보관은 호출하는 쪽에서 CommentHistory로 남긴다 */
    public void edit(String newContent) {
        if (newContent == null || newContent.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력하세요.");
        }
        this.content = newContent.trim();
        this.editedAt = LocalDateTime.now();
    }

    /** 이 댓글을 고치거나 지울 수 있는 사람인지 — 작성자 본인이거나 사장님 */
    public boolean canBeModifiedBy(Member member) {
        return member.isOwner() || author.equals(member.getDisplayName());
    }

    public Long getId() { return id; }
    public DailyReport getReport() { return report; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getEditedAt() { return editedAt; }
}
