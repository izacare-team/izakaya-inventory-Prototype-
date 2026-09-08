package com.izacare.repository;

import com.izacare.domain.DiningTable;
import com.izacare.domain.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테이블을 지울 수 있는지 판단하는 조회들.
 * 예약이 참조하는 테이블을 물리 삭제하면 외래키에 막혀 "이미 처리된 요청입니다"라는
 * 엉뚱한 메시지만 나왔다 — 그래서 참조 여부를 미리 보고 감추기로 했다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class DiningTableRepositoryTest {

    private static final Long 가게 = 1L;

    @Autowired DiningTableRepository tableRepository;
    @Autowired ReservationRepository reservationRepository;

    private DiningTable 테이블(int number, int capacity) {
        return tableRepository.save(new DiningTable(가게, number, capacity));
    }

    private Reservation 예약(DiningTable table) {
        return reservationRepository.save(new Reservation(
                LocalDate.of(2026, 9, 4), "18:00", 2, List.of(table), "손님", null, null));
    }

    @Test
    void 예약이_없는_테이블은_참조가_없다() {
        DiningTable t = 테이블(1, 2);
        assertThat(reservationRepository.existsByTables_Id(t.getId())).isFalse();
    }

    @Test
    void 공석_처리해도_지난_예약의_참조는_남는다() {
        DiningTable t = 테이블(2, 4);
        Reservation r = 예약(t);
        r.release();
        reservationRepository.flush();

        // 사용중은 아니지만 기록이 남아 있어 물리 삭제는 못 한다
        assertThat(reservationRepository.existsByStoreIdAndStatusAndTables_Id(
                가게, Reservation.Status.ACTIVE, t.getId())).isFalse();
        assertThat(reservationRepository.existsByTables_Id(t.getId())).isTrue();
    }

    @Test
    void 사용중인_예약은_상태로_구분된다() {
        DiningTable t = 테이블(3, 4);
        예약(t);
        assertThat(reservationRepository.existsByStoreIdAndStatusAndTables_Id(
                가게, Reservation.Status.ACTIVE, t.getId())).isTrue();
    }

    @Test
    void 감춘_테이블은_목록에서_빠진다() {
        DiningTable 남길것 = 테이블(4, 2);
        DiningTable 감출것 = 테이블(5, 2);
        감출것.deactivate();
        tableRepository.flush();

        assertThat(tableRepository.findByStoreIdAndActiveTrueOrderByTableNumberAsc(가게))
                .extracting(DiningTable::getId)
                .containsExactly(남길것.getId());
    }

    @Test
    void 감춘_번호를_다시_등록하면_되살아난다() {
        DiningTable t = 테이블(6, 2);
        t.deactivate();
        tableRepository.flush();

        // 유니크 제약 때문에 새 행을 넣을 수 없으므로, 같은 행을 새 정원으로 되살린다
        DiningTable found = tableRepository.findByStoreIdAndTableNumber(가게, 6).orElseThrow();
        found.reactivate(6);
        tableRepository.flush();

        assertThat(found.getId()).isEqualTo(t.getId());
        assertThat(found.getCapacity()).isEqualTo(6);
        assertThat(tableRepository.findByStoreIdAndActiveTrueOrderByTableNumberAsc(가게))
                .extracting(DiningTable::getTableNumber).contains(6);
    }
}
