package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 가게(매장) — 멀티테넌트의 최상위 단위.
 * 회원가입 시 사장님이 만들면 고유 코드가 발급되고, 모든 데이터(직원·재고·예약…)가 이 가게에 소속된다.
 */
@Entity
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 가게 고유 코드 — 로그인·직원 가입 시 사용 */
    @Column(nullable = false, unique = true)
    private String code;

    /** 영업 시간 (가게 설정) */
    private LocalTime businessOpen;
    private LocalTime businessClose;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Store() {}

    public Store(String name, String code) {
        this.name = name;
        this.code = code;
        this.businessOpen = LocalTime.of(17, 0);
        this.businessClose = LocalTime.of(23, 0);
    }

    public void rename(String name) { this.name = name; }
    public void changeCode(String code) { this.code = code; }
    public void setBusinessHours(LocalTime open, LocalTime close) {
        this.businessOpen = open;
        this.businessClose = close;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public LocalTime getBusinessOpen() { return businessOpen; }
    public LocalTime getBusinessClose() { return businessClose; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
