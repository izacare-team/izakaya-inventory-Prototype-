package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 회원: 사장님(OWNER)은 가입 즉시 사용, 알바생(STAFF)은 사장님 승인 후 사용.
 * 모든 회원은 하나의 가게(Store)에 소속된다. 아이디(username)는 전체에서 유니크하다(로그인 시 코드 불필요).
 */
@Entity
public class Member {

    public enum Role { OWNER, STAFF }
    /** PENDING=승인대기, ACTIVE=근무중, REJECTED=가입거절, RESIGNED=퇴직 */
    public enum Status { PENDING, ACTIVE, REJECTED, RESIGNED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Store store;

    /** 파생 쿼리(findByStoreId...)를 위해 FK 컬럼을 읽기 전용으로 매핑 (저장은 store 연관으로 처리) */
    @Column(name = "store_id", insertable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Member() {}

    public Member(Store store, String username, String passwordHash,
                  String displayName, Role role, Status status) {
        this.store = store;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = status;
    }

    public void approve() { this.status = Status.ACTIVE; }
    public void reject() { this.status = Status.REJECTED; }

    /** 퇴직 처리 — 로그인 및 모든 API 접근 권한이 즉시 사라진다 */
    public void resign() { this.status = Status.RESIGNED; }

    public boolean isOwner() { return role == Role.OWNER; }

    public Long getId() { return id; }
    public Store getStore() { return store; }
    public Long getStoreId() { return store.getId(); }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
