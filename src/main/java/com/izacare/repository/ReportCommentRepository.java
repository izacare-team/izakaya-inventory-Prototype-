package com.izacare.repository;

import com.izacare.domain.ReportComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportCommentRepository extends JpaRepository<ReportComment, Long> {
    List<ReportComment> findByReportIdOrderByCreatedAtAsc(Long reportId);
}
