package com.izacare.repository;

import com.izacare.domain.ReportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {
    List<ReportHistory> findByReportIdOrderByEditedAtDesc(Long reportId);
}
