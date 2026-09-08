package com.izacare.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
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

    /** 영업시간을 아직 안 정했거나 자정 전에 닫는 매장의 영업일 경계 */
    private static final LocalTime DEFAULT_DAY_CUTOFF = LocalTime.of(6, 0);
    /** 경계를 이보다 늦게 잡지는 않는다 — 아침 근무까지 전날로 삼켜 버리면 곤란하다 */
    private static final LocalTime MAX_DAY_CUTOFF = LocalTime.of(9, 0);
    /** 마감 후 정리 근무까지 같은 영업일로 묶어 주는 여유 */
    private static final int CLOSING_GRACE_HOURS = 2;

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

    /**
     * 이 시각이 속한 영업일.
     * 심야 영업이라 새벽 2시 퇴근이 흔한데, 달력 날짜를 그대로 쓰면 그게 "다음 날"이 되어
     * 출근 기록이 없는 새 날짜에 퇴근을 찍으려다 거부당한다. 경계 시각 이전은 전날로 친다.
     */
    public LocalDate businessDayOf(LocalDateTime now) {
        return now.toLocalTime().isBefore(dayCutoff())
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
    }

    /**
     * 영업일이 넘어가는 시각 — 이 시각 이전은 아직 전날 영업일이다.
     * 기본은 새벽 6시. 새벽 6시까지 출근을 찍는 이자카야는 없으니 이 정도면 안전하다.
     * 마감이 유난히 늦은 매장(예: 새벽 5시 마감)만 그만큼 경계를 뒤로 늘린다. 앞당기지는 않는다.
     */
    LocalTime dayCutoff() {
        // 종료가 개점보다 이르면 자정을 넘겨 영업하는 매장
        if (businessOpen != null && businessClose != null && businessClose.isBefore(businessOpen)) {
            LocalTime cutoff = businessClose.plusHours(CLOSING_GRACE_HOURS);
            // isAfter(businessClose): 여유를 더하다 자정을 또 넘긴 이상한 설정은 버린다
            if (cutoff.isAfter(businessClose) && cutoff.isAfter(DEFAULT_DAY_CUTOFF)) {
                return cutoff.isAfter(MAX_DAY_CUTOFF) ? MAX_DAY_CUTOFF : cutoff;
            }
        }
        return DEFAULT_DAY_CUTOFF;
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
