package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 공지 확인 기록 — 한 사람이 같은 공지를 두 번 확인할 수 없음 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"notice_id", "member_id"}))
public class NoticeAck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Member member;

    private LocalDateTime ackedAt = LocalDateTime.now();

    protected NoticeAck() {}

    public NoticeAck(Notice notice, Member member) {
        this.notice = notice;
        this.member = member;
    }

    public Long getId() { return id; }
    public Notice getNotice() { return notice; }
    public Member getMember() { return member; }
    public LocalDateTime getAckedAt() { return ackedAt; }
}
