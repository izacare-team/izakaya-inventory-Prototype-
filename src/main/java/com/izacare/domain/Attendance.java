package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 출퇴근 기록: 직원 1명 x 하루 1건.
 * 출근 → 휴게 시작 → 휴게 종료 → 퇴근 순서로만 기록되며, 각 항목은 하루에 한 번만 찍을 수 있다.
 */
@Entity
// 1인 1일 1건을 DB가 강제한다. 앱 코드의 mark() 검사는 "이미 있는 행"만 막을 수 있어서,
// 동시에 들어온 요청이 각자 새 행을 만드는 것은 못 막는다 (연타 한 번에 6건이 생긴 적 있음).
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "staff_name", "work_date"}))
public class Attendance {

    /**
     * 출근을 찍은 위치의 확인 결과.
     * 반경 밖이어도 출근은 정상 처리하고 기록만 남긴다 — GPS 오차로 진짜 출근한 직원이
     * 못 찍으면 급여 문제가 되기 때문. 억지력은 "못 찍게 막는다"가 아니라 "찍히면 남는다"에서 나온다.
     */
    public enum LocationCheck {
        GPS_OK,    // 매장 반경 안
        IP_OK,     // GPS는 못 받았거나 반경 밖이지만 매장 Wi-Fi에서 접속함
        OUTSIDE,   // 좌표는 받았는데 반경 밖이고 매장 IP도 아님
        UNKNOWN    // 위치 권한 거부/미지원 — 확인할 수 없었음
    }

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

    /** 출근 시점 위치 확인 결과 (퇴근·휴게는 확인하지 않는다) */
    @Enumerated(EnumType.STRING)
    private LocationCheck clockInLocation;

    /**
     * 출근 시점의 매장까지 거리(m). 좌표를 못 받았으면 null.
     * 위경도 자체는 저장하지 않는다 — 판단에 필요한 건 거리뿐이고,
     * 좌표를 남기면 근로자 개인위치정보를 보관하는 셈이 되기 때문.
     */
    private Integer clockInDistance;

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

    /** 출근 직후 위치 확인 결과를 남긴다 */
    public void recordClockInLocation(LocationCheck check, Integer distanceMeters) {
        this.clockInLocation = check;
        this.clockInDistance = distanceMeters;
    }

    /** 사장님이 확인해봐야 하는 출근인지 (매장 밖 또는 확인 불가) */
    public boolean isLocationSuspicious() {
        return clockInLocation == LocationCheck.OUTSIDE || clockInLocation == LocationCheck.UNKNOWN;
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
    public LocationCheck getClockInLocation() { return clockInLocation; }
    public Integer getClockInDistance() { return clockInDistance; }
}
