package com.izacare.repository;

import com.izacare.domain.WorkSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    List<WorkSchedule> findByStoreIdAndWorkDateOrderByStartTimeAsc(Long storeId, LocalDate date);
    List<WorkSchedule> findByStoreIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
            Long storeId, LocalDate start, LocalDate end);
}
