package com.izacare.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 출근 위치 확인의 거리 계산 — 여기가 틀리면 정상 출근이 '매장 밖'으로 찍힌다 */
class StoreLocationTest {

    /** 서울 시청 근처 기준점 */
    private Store storeAt(double lat, double lng, int radius) {
        Store s = new Store("테스트점", "TEST01");
        s.setAttendanceLocation(lat, lng, radius);
        return s;
    }

    @Test
    void 같은_지점이면_거리가_0이다() {
        Store s = storeAt(37.5665, 126.9780, 200);
        assertThat(s.distanceMeters(37.5665, 126.9780)).isZero();
    }

    @Test
    void 위도_0_001도는_약_111m다() {
        Store s = storeAt(37.5665, 126.9780, 200);
        // 위도 1도 ≈ 111km 이므로 0.001도 ≈ 111m — 오차 2m 이내면 계산식이 맞다
        assertThat(s.distanceMeters(37.5675, 126.9780)).isBetween(109, 113);
    }

    @Test
    void 반경_안팎을_구분한다() {
        Store s = storeAt(37.5665, 126.9780, 200);
        assertThat(s.isWithinRadius(s.distanceMeters(37.5666, 126.9781))).isTrue();   // 십수 m
        assertThat(s.isWithinRadius(s.distanceMeters(37.5765, 126.9780))).isFalse();  // 약 1.1km
    }

    @Test
    void 매장_좌표가_없으면_거리를_알_수_없다() {
        assertThat(new Store("테스트점", "TEST01").distanceMeters(37.5665, 126.9780)).isNull();
    }

    @Test
    void 매장_IP가_없으면_어떤_IP도_통과하지_않는다() {
        Store s = new Store("테스트점", "TEST01");
        assertThat(s.matchesAllowedIp("1.2.3.4")).isFalse();

        s.setAllowedIp("1.2.3.4");
        assertThat(s.matchesAllowedIp("1.2.3.4")).isTrue();
        assertThat(s.matchesAllowedIp("5.6.7.8")).isFalse();

        s.setAllowedIp("  ");   // 공백은 해제로 본다
        assertThat(s.matchesAllowedIp("1.2.3.4")).isFalse();
    }

    @Test
    void 터무니없는_반경은_거부한다() {
        assertThatThrownBy(() -> storeAt(37.5665, 126.9780, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storeAt(37.5665, 126.9780, 99999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 반경_밖_출근도_기록만_남고_출근_자체는_유효하다() {
        Attendance a = new Attendance(1L, "김알바", java.time.LocalDate.now());
        a.mark("clock-in", java.time.LocalTime.of(18, 0));
        a.recordClockInLocation(Attendance.LocationCheck.OUTSIDE, 1200);

        assertThat(a.getClockIn()).isEqualTo(java.time.LocalTime.of(18, 0));  // 출근은 막지 않는다
        assertThat(a.isLocationSuspicious()).isTrue();
        assertThat(a.getClockInDistance()).isEqualTo(1200);
    }
}
