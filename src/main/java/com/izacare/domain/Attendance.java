package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 출퇴근 기록: 직원 1명 x 하루 1건.
 * 출근 → 휴게 시작 → 휴게 종료 → 퇴근 순서로만 기록되며, 각 항목은 하루에 한 번만 찍을 수 있다.
 */
@Entity
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String staffName;

    @Column(nullable = false)
    private LocalDate workDate;

    private LocalTime clockIn;
    private LocalTime breakAt;
    private LocalTime breakEnd;
    private LocalTime clockOut;

    protected Attendance() {}

    public Attendance(Long storeId, String staffName, LocalDate workDate) {
        this.storeId = storeId;
        this.staffName = staffName;
        this.workDate = workDate;
    }

    public void mark(String action, LocalTime time) {
        switch (action) {
            case "clock-in" -> {
                require(clockIn == null, "이미 출근을 찍었습니다.");
                this.clockIn = time;
            }
            case "break" -> {
                require(clockIn != null, "출근을 먼저 찍어주세요.");
                require(breakAt == null, "이미 휴게를 시작했습니다.");
                require(clockOut == null, "이미 퇴근했습니다.");
                this.breakAt = time;
            }
            case "break-end" -> {
                require(breakAt != null, "휴게 시작을 먼저 찍어주세요.");
                require(breakEnd == null, "이미 휴게를 종료했습니다.");
                require(clockOut == null, "이미 퇴근했습니다.");
                this.breakEnd = time;
            }
            case "clock-out" -> {
                require(clockIn != null, "출근을 먼저 찍어주세요.");
                require(clockOut == null, "이미 퇴근을 찍었습니다.");
                require(breakAt == null || breakEnd != null, "휴게 종료를 먼저 찍어주세요.");
                this.clockOut = time;
            }
            default -> throw new IllegalArgumentException("알 수 없는 동작: " + action);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public String getStaffName() { return staffName; }
    public LocalDate getWorkDate() { return workDate; }
    public LocalTime getClockIn() { return clockIn; }
    public LocalTime getBreakAt() { return breakAt; }
    public LocalTime getBreakEnd() { return breakEnd; }
    public LocalTime getClockOut() { return clockOut; }
}
