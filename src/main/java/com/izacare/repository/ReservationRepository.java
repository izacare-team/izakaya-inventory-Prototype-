package com.izacare.repository;

import com.izacare.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 해당 가게·날짜에 그 테이블을 아직 사용중(ACTIVE)인 예약이 있는지.
     * 시간대와 무관하게, 근무자가 공석 처리하기 전까지는 계속 점유된 것으로 본다.
     */
    boolean existsByStoreIdAndReserveDateAndStatusAndTables_Id(
            Long storeId, LocalDate date, Reservation.Status status, Long tableId);

    List<Reservation> findByStoreIdAndReserveDateOrderByTimeSlotAsc(Long storeId, LocalDate date);

    List<Reservation> findByStoreIdAndReserveDateBetweenOrderByReserveDateAscTimeSlotAsc(
            Long storeId, LocalDate start, LocalDate end);

    /** 예약 확인 목록 — 오늘 이후 예약을 날짜·시간순으로 */
    List<Reservation> findByStoreIdAndReserveDateGreaterThanEqualOrderByReserveDateAscTimeSlotAsc(
            Long storeId, LocalDate from);

    /** 날짜와 무관하게 이 테이블을 아직 사용중인 예약이 있는지 — 테이블 삭제 가능 여부 판단 */
    boolean existsByStoreIdAndStatusAndTables_Id(
            Long storeId, Reservation.Status status, Long tableId);

    /** 이 테이블을 쓴 예약이 하나라도 있는지 — 지난 예약 포함. 테이블을 지울 수 있는지 판단한다 */
    boolean existsByTables_Id(Long tableId);

    /** 자동 공석 처리 스케줄러 대상 — 시간 제한이 있는 코스로 아직 사용중인 예약 */
    List<Reservation> findByStatusAndCourseDurationMinutesIsNotNull(Reservation.Status status);
}
