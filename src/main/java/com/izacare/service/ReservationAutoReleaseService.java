package com.izacare.service;

import com.izacare.domain.Reservation;
import com.izacare.repository.ReservationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 시간 제한이 있는 코스(예: 120분 코스)는 그 시간이 지나면 근무자가 놓쳐도 자동으로 공석 처리한다. */
@Service
public class ReservationAutoReleaseService {

    private final ReservationRepository reservationRepository;

    public ReservationAutoReleaseService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void releaseExpiredCourses() {
        LocalDateTime now = LocalDateTime.now();
        for (Reservation r : reservationRepository.findByStatusAndCourseDurationMinutesIsNotNull(Reservation.Status.ACTIVE)) {
            if (r.isDueForAutoRelease(now)) {
                r.release();
            }
        }
    }
}
