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

    /** 매장 위치 — 출근 위치 확인용. 사장님이 매장에서 "현재 위치로 설정"을 누르면 채워진다 */
    private Double latitude;
    private Double longitude;

    /**
     * 출근을 "매장에서 찍었다"고 볼 반경(m).
     * 실내·지하는 GPS 오차가 커서 기본값을 넉넉히 잡는다 — 매장마다 실측해서 조이는 게 맞다.
     */
    // 기본값을 DB 쪽에도 준다 — 파일 DB에는 이미 행이 있어서, 기본값 없이 NOT NULL 컬럼을 붙이면
    // ddl-auto=update 가 ALTER TABLE 에서 실패한다 (인메모리일 때는 매번 새로 만들어서 안 겪던 문제)
    @Column(nullable = false, columnDefinition = "integer default 200")
    private int attendanceRadius = DEFAULT_ATTENDANCE_RADIUS;

    /** 매장 Wi-Fi의 공인 IP — GPS가 안 잡히는 실내에서 이걸로 대신 확인한다 */
    private String allowedIp;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Store() {}

    public static final int DEFAULT_ATTENDANCE_RADIUS = 200;
    private static final int EARTH_RADIUS_M = 6_371_000;

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

    public void setAttendanceLocation(Double latitude, Double longitude, Integer radius) {
        this.latitude = latitude;
        this.longitude = longitude;
        if (radius != null) {
            if (radius < 10 || radius > 5000) {
                throw new IllegalArgumentException("반경은 10m ~ 5000m 사이로 정해주세요.");
            }
            this.attendanceRadius = radius;
        }
    }

    public void setAllowedIp(String allowedIp) {
        this.allowedIp = (allowedIp == null || allowedIp.isBlank()) ? null : allowedIp.trim();
    }

    /** 매장까지의 거리(m). 매장 좌표가 아직 설정되지 않았으면 null */
    public Integer distanceMeters(double lat, double lng) {
        if (latitude == null || longitude == null) return null;
        double dLat = Math.toRadians(lat - latitude);
        double dLng = Math.toRadians(lng - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return (int) Math.round(EARTH_RADIUS_M * 2 * Math.asin(Math.min(1, Math.sqrt(a))));
    }

    public boolean isWithinRadius(int distanceMeters) {
        return distanceMeters <= attendanceRadius;
    }

    /** 매장 Wi-Fi에서 접속했는지. 매장 IP가 설정 안 됐으면 항상 false(= 확인 불가) */
    public boolean matchesAllowedIp(String ip) {
        return allowedIp != null && allowedIp.equals(ip);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public LocalTime getBusinessOpen() { return businessOpen; }
    public LocalTime getBusinessClose() { return businessClose; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public int getAttendanceRadius() { return attendanceRadius; }
    public String getAllowedIp() { return allowedIp; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
