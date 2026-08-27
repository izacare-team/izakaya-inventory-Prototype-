package com.izacare.repository;

import com.izacare.domain.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByStoreIdAndStaffNameAndWorkDate(Long storeId, String staffName, LocalDate workDate);

    /** 오늘 출근 기록이 있는 인원 (대시보드용) */
    List<Attendance> findByStoreIdAndWorkDate(Long storeId, LocalDate workDate);
}
