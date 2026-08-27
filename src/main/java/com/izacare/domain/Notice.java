package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 홈 화면 공지 (예: 회식 8/21, 단체예약 8/30) */
@Entity
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false, length = 2000)
    private String title;

    private LocalDate eventDate;

    /** 수정된 적이 있으면 시각이 들어감 → 화면에 "수정됨" 표시 */
    private LocalDateTime editedAt;

    protected Notice() {}

    public Notice(Long storeId, String title, LocalDate eventDate) {
        this.storeId = storeId;
        this.title = title;
        this.eventDate = eventDate;
    }

    /** 수정 — 이전 값 보존은 NoticeHistory에서 처리 */
    public void edit(String title, LocalDate eventDate) {
        this.title = title;
        this.eventDate = eventDate;
        this.editedAt = LocalDateTime.now();
    }

    public boolean isEdited() { return editedAt != null; }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getTitle() { return title; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalDateTime getEditedAt() { return editedAt; }
}
