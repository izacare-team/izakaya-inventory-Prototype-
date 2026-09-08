package com.izacare.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영업일 판정 — 여기가 틀리면 자정 넘겨 일한 직원이 퇴근을 못 찍는다.
 * (달력 날짜가 바뀌면서 "출근 기록이 없는 새 날짜"에 퇴근을 찍으려다 거부당했다)
 */
class StoreBusinessDayTest {

    private static final LocalDate 금요일 = LocalDate.of(2026, 9, 4);
    private static final LocalDate 토요일 = LocalDate.of(2026, 9, 5);

    private Store store(LocalTime open, LocalTime close) {
        Store s = new Store("테스트점", "TEST01");
        s.setBusinessHours(open, close);
        return s;
    }

    @Test
    void 새벽_퇴근은_전날_영업일로_친다() {
        Store s = store(LocalTime.of(17, 0), LocalTime.of(23, 0));
        // 금요일 18시 출근 → 토요일 새벽 2시 퇴근. 둘 다 금요일 근무여야 1건으로 집계된다
        assertThat(s.businessDayOf(LocalDateTime.of(금요일, LocalTime.of(18, 0)))).isEqualTo(금요일);
        assertThat(s.businessDayOf(LocalDateTime.of(토요일, LocalTime.of(2, 0)))).isEqualTo(금요일);
    }

    @Test
    void 경계_이후_출근은_당일_영업일이다() {
        Store s = store(LocalTime.of(17, 0), LocalTime.of(23, 0));
        assertThat(s.businessDayOf(LocalDateTime.of(토요일, LocalTime.of(6, 0)))).isEqualTo(토요일);
        assertThat(s.businessDayOf(LocalDateTime.of(토요일, LocalTime.of(11, 0)))).isEqualTo(토요일);
    }

    @Test
    void 마감이_늦은_매장은_경계도_늦춘다() {
        // 새벽 5시 마감 → 마감 정리까지 감안해 7시까지 전날로 본다
        Store s = store(LocalTime.of(18, 0), LocalTime.of(5, 0));
        assertThat(s.dayCutoff()).isEqualTo(LocalTime.of(7, 0));
        assertThat(s.businessDayOf(LocalDateTime.of(토요일, LocalTime.of(6, 30)))).isEqualTo(금요일);
        assertThat(s.businessDayOf(LocalDateTime.of(토요일, LocalTime.of(7, 30)))).isEqualTo(토요일);
    }

    @Test
    void 경계를_기본값보다_앞당기지는_않는다() {
        // 새벽 2시 마감이어도 경계는 기본 6시 그대로 — 앞당겨봐야 득이 없고 오판만 늘어난다
        Store s = store(LocalTime.of(18, 0), LocalTime.of(2, 0));
        assertThat(s.dayCutoff()).isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    void 경계는_아침_9시를_넘지_않는다() {
        // 마감을 8시로 잘못 넣어도 아침 근무까지 전날로 삼키면 안 된다
        Store s = store(LocalTime.of(18, 0), LocalTime.of(8, 0));
        assertThat(s.dayCutoff()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void 영업시간_미설정이면_기본_경계를_쓴다() {
        Store s = store(null, null);
        assertThat(s.dayCutoff()).isEqualTo(LocalTime.of(6, 0));
        assertThat(s.businessDayOf(LocalDateTime.of(토요일, LocalTime.of(3, 0)))).isEqualTo(금요일);
    }
}
