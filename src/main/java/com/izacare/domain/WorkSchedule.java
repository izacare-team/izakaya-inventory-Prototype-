package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/** 알바 근무 스케줄 — 사장님이 등록하면 해당 직원에게 알림이 간다 */
@Entity
public class WorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 (직원에서 파생) */
    @Column(nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Member member;

    @Column(nullable = false)
    private LocalDate workDate;

    private LocalTime startTime;
    private LocalTime endTime;

    private String memo;

    protected WorkSchedule() {}

    public WorkSchedule(Member member, LocalDate workDate,
                        LocalTime startTime, LocalTime endTime, String memo) {
        this.member = member;
        this.storeId = member.getStoreId();
        this.workDate = workDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.memo = memo;
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Member getMember() { return member; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getMemo() { return memo; }
}
