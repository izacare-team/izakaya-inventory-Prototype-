package com.izacare.domain;

import jakarta.persistence.*;

/**
 * 코스 메뉴 — 가게마다 사장님이 등록하는 코스 목록 (예: "모둠사시미 코스 (120분)")
 *
 * 두 가지 성격을 따로 관리한다.
 *  - unlimitedRefill : 무제한 리필 코스인지 여부 (화면에서 색으로 구분되는 기준)
 *  - durationMinutes : 이용 시간 제한. 값이 있으면 그 시간이 지나면 테이블이 자동 공석 처리된다.
 * 무제한 리필이면서 시간 제한이 있는 코스(예: 120분 무한리필)도 표현할 수 있다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "name"}))
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    private Integer durationMinutes;

    /** 무제한 리필 코스 여부 — 일반 코스와 구분해 표시한다 */
    @Column(nullable = false)
    private boolean unlimitedRefill;

    protected Course() {}

    public Course(Long storeId, String name, Integer durationMinutes) {
        this(storeId, name, durationMinutes, false);
    }

    public Course(Long storeId, String name, Integer durationMinutes, boolean unlimitedRefill) {
        this.storeId = storeId;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.unlimitedRefill = unlimitedRefill;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getName() { return name; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public boolean isUnlimitedRefill() { return unlimitedRefill; }
}
