package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 종 아이콘에 모이는 알림.
 * 재고 부족은 재고에서 실시간 파생하므로 저장하지 않고,
 * 예약 등록·스케줄 등록처럼 "발생 시점"이 중요한 이벤트만 저장한다.
 */
@Entity
public class Notification {

    public enum Type {
        RESERVATION(false),   // 새 예약 — 홀 근무자 모두 알아야 함
        SCHEDULE(false),      // 근무 스케줄 등록 — 당사자에게만 발송
        SIGNUP(true),         // 알바생 가입 신청 — 승인 권한이 사장님에게만 있음
        MEMBER(false),        // 승인·복직 통보는 당사자에게 가야 하므로 전용이 아님
                              // (합류 소식 전파는 notifyOwners로 사장님에게만 보낸다)
        NOTICE(false),        // 새 공지 — 전원
        REPORT(true),         // 새 일보 — 확인·피드백은 사장님 몫
        DISPOSE(true);        // 재고 폐기 (손실 추적) — 경영 정보

        /** 사장님만 받아보는 알림인지 */
        private final boolean ownerOnly;

        Type(boolean ownerOnly) { this.ownerOnly = ownerOnly; }

        public boolean isOwnerOnly() { return ownerOnly; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false, length = 500)
    private String message;

    private boolean readFlag = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Notification() {}

    public Notification(Member recipient, Type type, String message) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
    }

    public void markRead() { this.readFlag = true; }

    public Long getId() { return id; }
    public Member getRecipient() { return recipient; }
    public Type getType() { return type; }
    public String getMessage() { return message; }
    public boolean isReadFlag() { return readFlag; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
