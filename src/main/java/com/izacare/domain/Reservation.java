package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 예약: 날짜 + 시간대 + 테이블(들).
 * - 단체 손님은 여러 테이블을 붙여 앉을 수 있다. (예: 4명 → 2인석 2개)
 * - 손님이 언제 나갈지 알 수 없으므로, 예약이 잡히면 근무자가 직접
 *   "공석 처리"할 때까지 그 테이블은 계속 사용중(ACTIVE)으로 잡아 둔다.
 */
@Entity
public class Reservation {

    public enum Status { ACTIVE, RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소속 가게 */
    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private LocalDate reserveDate;

    @Column(nullable = false)
    private String timeSlot;      // "18:00" 형식 (도착 예정 시간)

    private int people;

    /** 배정된 테이블 목록 — 여러 개 선택 가능 */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "reservation_tables",
            joinColumns = @JoinColumn(name = "reservation_id"),
            inverseJoinColumns = @JoinColumn(name = "dining_table_id"))
    private List<DiningTable> tables = new ArrayList<>();

    private String customerName;

    /** 선택한 코스 이름 — null/빈값이면 "코스 없이 예약" */
    private String courseName;

    /** 예약 시점의 코스 시간 제한(분) — null이면 시간 제한 없음(자동 공석 처리 대상 아님) */
    private Integer courseDurationMinutes;

    /** ACTIVE = 테이블 사용중 / RELEASED = 근무자가 공석 처리함 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private LocalDateTime releasedAt;

    protected Reservation() {}

    public Reservation(LocalDate reserveDate, String timeSlot, int people,
                       List<DiningTable> tables, String customerName, String courseName,
                       Integer courseDurationMinutes) {
        this.storeId = tables.isEmpty() ? null : tables.get(0).getStoreId();
        this.reserveDate = reserveDate;
        this.timeSlot = timeSlot;
        this.people = people;
        this.tables = new ArrayList<>(tables);
        this.customerName = customerName;
        this.courseName = (courseName == null || courseName.isBlank()) ? null : courseName;
        this.courseDurationMinutes = this.courseName == null ? null : courseDurationMinutes;
    }

    /** 근무자가 손님 퇴장 후 공석 처리 — 이때부터 테이블을 다시 배정할 수 있다 */
    public void release() {
        if (status == Status.RELEASED) {
            throw new IllegalStateException("이미 공석 처리된 예약입니다.");
        }
        this.status = Status.RELEASED;
        this.releasedAt = LocalDateTime.now();
    }

    public boolean isActive() { return status == Status.ACTIVE; }

    /** 코스 시간이 다 되었으면 자동 공석 처리 대상인지 — 스케줄러가 주기적으로 확인한다 */
    public boolean isDueForAutoRelease(LocalDateTime now) {
        if (status != Status.ACTIVE || courseDurationMinutes == null) return false;
        LocalDateTime end = LocalDateTime.of(reserveDate, LocalTime.parse(timeSlot)).plusMinutes(courseDurationMinutes);
        return !now.isBefore(end);
    }

    /** 배정된 테이블들의 총 수용 인원 */
    public int totalCapacity() {
        return tables.stream().mapToInt(DiningTable::getCapacity).sum();
    }

    /** 화면 표시용 테이블 번호 목록 (가게 안에서의 번호) */
    public List<Integer> tableNumbers() {
        return tables.stream().map(DiningTable::getTableNumber).sorted().toList();
    }

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public LocalDate getReserveDate() { return reserveDate; }
    public String getTimeSlot() { return timeSlot; }
    public int getPeople() { return people; }
    public List<DiningTable> getTables() { return tables; }
    public String getCustomerName() { return customerName; }
    public String getCourseName() { return courseName; }
    public Integer getCourseDurationMinutes() { return courseDurationMinutes; }
    public Status getStatus() { return status; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
}
